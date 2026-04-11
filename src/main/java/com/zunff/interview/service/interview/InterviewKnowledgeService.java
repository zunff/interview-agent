package com.zunff.interview.service.interview;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.zunff.interview.model.dto.rag.KnowledgeSearchRequest;
import com.zunff.interview.model.dto.rag.KnowledgeSearchResult;
import com.zunff.interview.service.extend.PromptTemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 面试知识库服务
 * 基于 Spring AI VectorStore 实现的 RAG 检索服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewKnowledgeService {

    private final VectorStore vectorStore;
    private final ChatClient.Builder chatClientBuilder;
    private final PromptTemplateService promptTemplateService;

    /**
     * 检索相关面试题
     *
     * @param request 检索请求
     * @return 检索结果列表
     */
    public List<KnowledgeSearchResult> searchSimilarQuestions(KnowledgeSearchRequest request) {
        log.info("开始检索知识库, query={}, topK={}", request.getQuery(), request.getTopK());

        // 构建数据库层过滤表达式（字符串形式）
        String filterExpression = buildFilterExpression(request);

        // 构建搜索请求（带数据库层过滤）
        SearchRequest.Builder requestBuilder = SearchRequest.builder()
                .query(request.getQuery())
                .topK(request.getTopK() > 0 ? request.getTopK() : 5);

        if (filterExpression != null && !filterExpression.isEmpty()) {
            requestBuilder.filterExpression(filterExpression);
        }

        // 执行向量检索
        List<Document> documents = vectorStore.similaritySearch(requestBuilder.build());

        // 转换结果（不再需要内存过滤）
        List<KnowledgeSearchResult> results = documents.stream()
                .map(this::toSearchResult)
                .collect(Collectors.toList());

        log.info("检索完成, 找到 {} 条相关题目", results.size());
        return results;
    }

    /**
     * 构建数据库层过滤表达式（字符串形式）
     */
    private String buildFilterExpression(KnowledgeSearchRequest request) {
        List<String> conditions = new ArrayList<>();

        // 题型过滤
        if (request.getQuestionType() != null && !request.getQuestionType().isEmpty()) {
            conditions.add("questionType == '" + escapeFilterValue(request.getQuestionType()) + "'");
        }

        // 公司过滤
        if (request.getCompany() != null && !request.getCompany().isEmpty()) {
            conditions.add("company == '" + escapeFilterValue(request.getCompany()) + "'");
        }

        // 岗位过滤
        if (request.getJobPosition() != null && !request.getJobPosition().isEmpty()) {
            conditions.add("jobPosition == '" + escapeFilterValue(request.getJobPosition()) + "'");
        }

        return conditions.isEmpty() ? null : String.join(" && ", conditions);
    }

    /**
     * 转义过滤表达式中的特殊字符
     */
    private String escapeFilterValue(String value) {
        return value.replace("'", "\\'");
    }

    /**
     * 根据岗位信息检索相关面试题
     * 自动提取公司、岗位等元数据进行过滤
     *
     * @param jobInfo       岗位信息
     * @param questionType  问题类型
     * @param topK          返回数量
     * @return 检索结果列表
     */
    public List<KnowledgeSearchResult> searchByJobInfo(String jobInfo, String questionType, int topK) {
        // 提取元数据
        KnowledgeFilter filter = extractMetadata(jobInfo);

        KnowledgeSearchRequest request = KnowledgeSearchRequest.builder()
                .query(jobInfo)
                .questionType(questionType)
                .company(filter.company)
                .jobPosition(filter.jobPosition)
                .topK(topK > 0 ? topK : 5)
                .build();

        return searchSimilarQuestions(request);
    }

    /**
     * 索引单个知识文档
     *
     * @param id        文档ID
     * @param question  问题内容
     * @param metadata  元数据
     */
    public void indexDocument(String id, String question, Map<String, Object> metadata) {
        Document document = new Document(id, question, metadata);
        vectorStore.add(List.of(document));
        log.debug("索引文档成功: id={}", id);
    }

    /**
     * 批量索引知识文档
     *
     * @param documents 文档列表
     */
    public void batchIndex(List<Document> documents) {
        vectorStore.add(documents);
        log.info("批量索引 {} 个文档成功", documents.size());
    }

    /**
     * 删除文档
     *
     * @param ids 文档ID列表
     */
    public void deleteDocuments(List<String> ids) {
        vectorStore.delete(ids);
        log.info("删除 {} 个文档成功", ids.size());
    }

    /**
     * 从岗位信息中提取元数据（公司、岗位）
     */
    private KnowledgeFilter extractMetadata(String jobInfo) {
        try {
            String systemPrompt = promptTemplateService.getPrompt("knowledge-filter");

            ChatClient chatClient = chatClientBuilder.build();

            String response = chatClient.prompt()
                    .system(systemPrompt)
                    .user("岗位信息：" + jobInfo)
                    .call()
                    .content();

            // 解析结果
            String jsonStr = extractJson(response);
            JSONObject json = JSONUtil.parseObj(jsonStr);

            return new KnowledgeFilter(
                    json.getStr("company"),
                    json.getStr("jobPosition")
            );

        } catch (Exception e) {
            log.warn("提取元数据失败: {}", e.getMessage());
            return new KnowledgeFilter(null, null);
        }
    }

    /**
     * 转换 Document 到 SearchResult
     */
    private KnowledgeSearchResult toSearchResult(Document doc) {
        Map<String, Object> metadata = doc.getMetadata();

        return KnowledgeSearchResult.builder()
                .question(doc.getText())
                .questionType((String) metadata.getOrDefault("questionType", ""))
                .company((String) metadata.getOrDefault("company", ""))
                .jobPosition((String) metadata.getOrDefault("jobPosition", ""))
                .category((String) metadata.getOrDefault("category", ""))
                .difficulty((String) metadata.getOrDefault("difficulty", ""))
                .similarityScore(doc.getScore() != null ? doc.getScore() : 0.0)
                .build();
    }

    /**
     * 提取 JSON 字符串
     */
    private String extractJson(String response) {
        int start = response.indexOf('{');
        int end = response.lastIndexOf('}');
        if (start != -1 && end != -1 && end > start) {
            return response.substring(start, end + 1);
        }
        return response;
    }

    /**
     * 知识过滤条件
     */
    private record KnowledgeFilter(String company, String jobPosition) {
    }
}