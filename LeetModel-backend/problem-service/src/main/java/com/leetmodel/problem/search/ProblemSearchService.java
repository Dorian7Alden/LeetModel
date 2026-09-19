package com.leetmodel.problem.search;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leetmodel.problem.dto.ProblemPageQuery;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.util.EntityUtils;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 题库全文检索查询。
 *
 * <p>检索只负责给相关题目排序并返回分页命中；题目事实仍从数据库读取。
 * 检索不可用时返回 null，由调用方降级到数据库查询。</p>
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "problem.search", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ProblemSearchService {

    private final RestClient client;
    private final ProblemSearchProperties properties;
    private final ObjectMapper objectMapper;

    public ProblemSearchService(RestClient problemSearchElasticsearchClient,
                                ProblemSearchProperties properties,
                                ObjectMapper objectMapper) {
        this.client = problemSearchElasticsearchClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /**
     * 按关键词与筛选条件检索题目。
     *
     * @param keyword 关键词
     * @param query 分页与筛选条件
     * @return 命中分页；检索不可用时返回 null
     */
    public ProblemSearchPage search(String keyword, ProblemPageQuery query) {
        int pageSize = Math.max(1, query.getPageSize());
        int from = Math.max(0, (query.getPage() - 1) * pageSize);
        if (from + pageSize > properties.getMaxResultWindow()) {
            return null;
        }
        try {
            Request request = new Request("POST", "/" + properties.getIndexName() + "/_search");
            request.setJsonEntity(objectMapper.writeValueAsString(buildQuery(keyword, query, from, pageSize)));
            Response response = client.performRequest(request);
            return parse(response);
        } catch (IOException | RuntimeException exception) {
            log.warn("题库全文检索不可用，降级到数据库查询: type={}",
                    exception.getClass().getSimpleName());
            return null;
        }
    }

    private Map<String, Object> buildQuery(String keyword, ProblemPageQuery query, int from, int pageSize) {
        List<Map<String, Object>> filters = new ArrayList<>();
        if (query.getStatus() != null) filters.add(term("status", query.getStatus()));
        if (query.getContestId() != null) filters.add(term("contestId", query.getContestId()));
        if (query.getYear() != null) filters.add(term("year", query.getYear()));
        if (query.getDifficulty() != null) filters.add(term("difficulty", query.getDifficulty()));
        if (query.getStatementLanguage() != null) {
            filters.add(term("statementLanguage", query.getStatementLanguage()));
        }
        if (query.getTagIds() != null) {
            // 每个标签一个条件，保持与结构化查询“同时命中全部标签”的语义一致
            for (Long tagId : query.getTagIds()) {
                if (tagId != null) filters.add(term("tagIds", tagId));
            }
        }

        Map<String, Object> bool = new LinkedHashMap<>();
        bool.put("filter", filters);
        bool.put("must", List.of(Map.of("multi_match", Map.of(
                "query", keyword,
                "fields", List.of("title^2", "content"),
                "type", "best_fields"))));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("query", Map.of("bool", bool));
        body.put("sort", List.of(
                Map.of("_score", Map.of("order", "desc")),
                Map.of("year", Map.of("order", "desc")),
                Map.of("code", Map.of("order", "asc"))));
        body.put("from", from);
        body.put("size", pageSize);
        body.put("track_total_hits", true);
        return body;
    }

    private Map<String, Object> term(String field, Object value) {
        return Map.of("term", Map.of(field, value));
    }

    private ProblemSearchPage parse(Response response) throws IOException {
        JsonNode root = objectMapper.readTree(EntityUtils.toString(response.getEntity()));
        long total = root.path("hits").path("total").path("value").asLong(0);
        List<Long> problemIds = new ArrayList<>();
        for (JsonNode hit : root.path("hits").path("hits")) {
            problemIds.add(hit.path("_id").asLong());
        }
        return new ProblemSearchPage(problemIds, total);
    }
}
