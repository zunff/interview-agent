package com.zunff.interview.common.response;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 分页结果封装
 *
 * @param <T> 数据类型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "分页结果")
public class PageResult<T> {

    @Schema(description = "数据列表")
    private List<T> records;

    @Schema(description = "总记录数")
    private long total;

    @Schema(description = "当前页码")
    private long page;

    @Schema(description = "每页大小")
    private long size;

    @Schema(description = "总页数")
    private long pages;

    /**
     * 从 MyBatis-Plus IPage 构建分页结果
     */
    public static <T> PageResult<T> of(IPage<?> page, List<T> records) {
        return PageResult.<T>builder()
                .records(records)
                .total(page.getTotal())
                .page(page.getCurrent())
                .size(page.getSize())
                .pages(page.getPages())
                .build();
    }
}
