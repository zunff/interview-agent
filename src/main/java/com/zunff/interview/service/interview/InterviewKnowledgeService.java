package com.zunff.interview.service.interview;

import com.zunff.interview.model.dto.rag.KnowledgeSearchRequest;
import com.zunff.interview.model.dto.rag.KnowledgeSearchResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.text.similarity.JaroWinklerSimilarity;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 面试知识库服务
 * 基于 Spring AI VectorStore 实现的 RAG 检索服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewKnowledgeService {

    private static final double WEIGHT_VECTOR = 0.6;
    private static final double WEIGHT_COMPANY = 0.2;
    private static final double WEIGHT_JOB = 0.2;

    private static final JaroWinklerSimilarity JARO_WINKLER = new JaroWinklerSimilarity();

    /**
     * 从长到短，避免先截短导致无法匹配更长后缀
     */
    private static final String[] COMPANY_SUFFIXES = {
            "股份有限公司", "有限责任公司", "科技有限公司", "有限公司", "集团公司", "集团", "股份",
            " incorporated", " limited", " ltd.", " ltd", " inc.", " inc", " corp.", " corp", " co.", " co"
    };

    private final VectorStore vectorStore;

    private record ScoredResult(KnowledgeSearchResult result, double score) {
    }


    /**
     * 构建数据库层过滤表达式（字符串形式）
     * 只保留 questionType 精确过滤，company 和 jobPosition 通过后处理重排序
     */
    private String buildFilterExpression(KnowledgeSearchRequest request) {
        // 只保留题型过滤
        if (request.getQuestionType() != null && !request.getQuestionType().isEmpty()) {
            return "questionType == '" + escapeFilterValue(request.getQuestionType()) + "'";
        }
        return null;
    }

    /**
     * 转义过滤表达式中的特殊字符
     */
    private String escapeFilterValue(String value) {
        return value.replace("'", "\\'");
    }

    /**
     * 根据岗位信息检索相关面试题
     *
     * @param jobInfo       岗位信息
     * @param questionType  问题类型
     * @param company       公司过滤（可空）
     * @param jobPosition   岗位过滤（可空）
     * @param topK          返回数量
     * @return 检索结果列表
     */
    public List<KnowledgeSearchResult> searchByJobInfo(
            String jobInfo,
            String questionType,
            String company,
            String jobPosition,
            int topK) {
        // 放宽过滤：获取更多候选结果用于重排序
        int candidateCount = (topK > 0 ? topK : 5) * 2;

        KnowledgeSearchRequest request = KnowledgeSearchRequest.builder()
                .query(jobInfo)
                .questionType(questionType)
                .company(company)
                .jobPosition(jobPosition)
                .topK(candidateCount)
                .build();

        log.info("开始检索知识库, query={}, candidateCount={}", request.getQuery(), candidateCount);

        // 构建数据库层过滤表达式（只过滤 questionType）
        String filterExpression = buildFilterExpression(request);

        // 构建搜索请求（带数据库层过滤）
        SearchRequest.Builder requestBuilder = SearchRequest.builder()
                .query(request.getQuery())
                .topK(candidateCount);

        if (filterExpression != null && !filterExpression.isEmpty()) {
            requestBuilder.filterExpression(filterExpression);
        }

        // 执行向量检索
        List<Document> documents = vectorStore.similaritySearch(requestBuilder.build());

        List<KnowledgeSearchResult> candidates = documents.stream()
                .map(this::toSearchResult)
                .toList();

        if (candidates.isEmpty()) {
            log.info("检索完成, 找到 0 条相关题目（从 0 条候选中重排序）");
            return List.of();
        }

        double minVec = candidates.stream().mapToDouble(KnowledgeSearchResult::getSimilarityScore).min().orElse(0.0);
        double maxVec = candidates.stream().mapToDouble(KnowledgeSearchResult::getSimilarityScore).max().orElse(0.0);

        String normTargetCompany = normalizeCompanyField(company);
        String normTargetJob = normalizeComparableField(jobPosition);
        boolean hasTargetCompany = !normTargetCompany.isEmpty();
        boolean hasTargetJob = !normTargetJob.isEmpty();

        int limit = topK > 0 ? topK : 5;
        List<KnowledgeSearchResult> results = candidates.stream()
                .map(r -> {
                    double normVector = normalizeVectorScoreInRange(r.getSimilarityScore(), minVec, maxVec);
                    double score = calculateRelevanceScore(r, normVector, normTargetCompany, hasTargetCompany,
                            normTargetJob, hasTargetJob);
                    return new ScoredResult(r, score);
                })
                .sorted(Comparator.comparingDouble(ScoredResult::score).reversed())
                .limit(limit)
                .map(ScoredResult::result)
                .toList();

        log.info("检索完成, 找到 {} 条相关题目（从 {} 条候选中重排序）", results.size(), documents.size());
        return results;
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
     * 将候选集内向量分数缩放到 [0,1]，便于与公司/岗位分数加权对齐
     */
    private static double normalizeVectorScoreInRange(double rawScore, double min, double max) {
        if (max <= min) {
            return 1.0;
        }
        return (rawScore - min) / (max - min);
    }

    /**
     * 计算结果的综合相关性分数
     * 结合向量相似度、公司相似度与岗位相似度；仅对「目标侧与文档侧均有值」的维度计入权重，避免无元数据条目被不合理压低。
     */
    private double calculateRelevanceScore(KnowledgeSearchResult result,
                                           double normalizedVectorScore,
                                           String normalizedTargetCompany,
                                           boolean hasTargetCompany,
                                           String normalizedTargetJob,
                                           boolean hasTargetJob) {
        double wVector = WEIGHT_VECTOR;
        double wCompany = 0.0;
        double companyPart = 0.0;
        double wJob = 0.0;
        double jobPart = 0.0;

        if (hasTargetCompany) {
            String docCompany = normalizeCompanyField(result.getCompany());
            if (!docCompany.isEmpty()) {
                wCompany = WEIGHT_COMPANY;
                companyPart = calculateEntityMatchScore(docCompany, normalizedTargetCompany);
            }
        }

        if (hasTargetJob) {
            String docJob = normalizeComparableField(result.getJobPosition());
            if (!docJob.isEmpty()) {
                wJob = WEIGHT_JOB;
                jobPart = calculateEntityMatchScore(docJob, normalizedTargetJob);
            }
        }

        double totalWeight = wVector + wCompany + wJob;
        if (totalWeight <= 0.0) {
            return 0.0;
        }
        return (normalizedVectorScore * wVector + companyPart * wCompany + jobPart * wJob) / totalWeight;
    }

    /**
     * 岗位/通用字段：去空白、统一大小写，不截业务后缀
     */
    private static String normalizeComparableField(String s) {
        if (s == null || s.isBlank()) {
            return "";
        }
        return s.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    /**
     * 公司名：在通用归一化基础上去常见工商/英文后缀
     */
    private static String normalizeCompanyField(String s) {
        String t = normalizeComparableField(s);
        if (t.isEmpty()) {
            return "";
        }
        boolean changed;
        do {
            changed = false;
            for (String suf : COMPANY_SUFFIXES) {
                if (t.endsWith(suf)) {
                    t = t.substring(0, t.length() - suf.length()).trim();
                    changed = true;
                    break;
                }
            }
        } while (changed);
        return t;
    }

    /**
     * 精确匹配、包含关系优先，其余走 Jaro-Winkler
     */
    private static double calculateEntityMatchScore(String normalizedLeft, String normalizedRight) {
        if (normalizedLeft.isEmpty() || normalizedRight.isEmpty()) {
            return 0.0;
        }
        if (normalizedLeft.equals(normalizedRight)) {
            return 1.0;
        }
        if (normalizedLeft.contains(normalizedRight) || normalizedRight.contains(normalizedLeft)) {
            return 0.85;
        }
        return JARO_WINKLER.apply(normalizedLeft, normalizedRight);
    }

}