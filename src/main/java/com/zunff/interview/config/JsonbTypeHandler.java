package com.zunff.interview.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

import java.sql.*;
import java.util.List;

/**
 * PostgreSQL jsonb 类型处理器
 * 使用 PGobject（运行时可用）确保 JDBC 参数类型正确设置为 jsonb，
 * 避免 PostgreSQL 拒绝 varchar → jsonb 的隐式转换。
 */
@MappedJdbcTypes(JdbcType.OTHER)
@MappedTypes(List.class)
public class JsonbTypeHandler extends BaseTypeHandler<List<String>> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, List<String> parameter, JdbcType jdbcType) throws SQLException {
        try {
            String json = MAPPER.writeValueAsString(parameter);
            // 反射创建 PGobject，因为 postgresql 依赖 scope=runtime 编译期不可见
            try {
                Object pgObject = Class.forName("org.postgresql.util.PGobject")
                        .getDeclaredConstructor().newInstance();
                pgObject.getClass().getMethod("setType", String.class).invoke(pgObject, "jsonb");
                pgObject.getClass().getMethod("setValue", String.class).invoke(pgObject, json);
                ps.setObject(i, pgObject);
            } catch (ReflectiveOperationException e) {
                // Fallback: 用 Types.OTHER 让 PG 驱动自动推断
                ps.setObject(i, json, Types.OTHER);
            }
        } catch (JsonProcessingException e) {
            throw new SQLException("Failed to serialize list to JSON", e);
        }
    }

    @Override
    public List<String> getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return parseJson(rs.getString(columnName));
    }

    @Override
    public List<String> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return parseJson(rs.getString(columnIndex));
    }

    @Override
    public List<String> getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return parseJson(cs.getString(columnIndex));
    }

    private List<String> parseJson(String json) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        try {
            return MAPPER.readValue(json, MAPPER.getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize JSON to List<String>", e);
        }
    }
}
