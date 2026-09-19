package com.leetmodel.problem.search;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpEntity;
import org.apache.http.util.EntityUtils;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.ResponseException;
import org.elasticsearch.client.RestClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 题库索引写入与维护。
 *
 * <p>索引只承担检索职责，题目事实仍以数据库为准；重建只读取数据库快照。</p>
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "problem.search", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ProblemSearchIndexService {

    private final RestClient client;
    private final ProblemSearchProperties properties;
    private final ObjectMapper objectMapper;

    public ProblemSearchIndexService(RestClient problemSearchElasticsearchClient,
                                     ProblemSearchProperties properties,
                                     ObjectMapper objectMapper) {
        this.client = problemSearchElasticsearchClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /**
     * 检查索引是否已存在。
     *
     * <p>低层客户端对 HEAD 的 404 不抛异常，只能按状态码判断。</p>
     *
     * @return 索引是否存在
     */
    public boolean indexExists() {
        try {
            Response response = client.performRequest(new Request("HEAD", "/" + properties.getIndexName()));
            return response.getStatusLine().getStatusCode() != 404;
        } catch (IOException exception) {
            log.warn("题库索引检查失败，全文检索将走数据库降级: type={}",
                    exception.getClass().getSimpleName());
            return false;
        }
    }

    /**
     * 写入或覆盖单个题目文档。
     *
     * @param document 题目检索文档
     * @return 是否写入成功
     */
    public boolean indexDocument(ProblemSearchDocument document) {
        try {
            Request request = new Request("PUT",
                    "/" + properties.getIndexName() + "/_doc/" + document.problemId());
            request.setJsonEntity(objectMapper.writeValueAsString(toMap(document)));
            client.performRequest(request);
            return true;
        } catch (IOException exception) {
            log.warn("题库索引写入失败，等待重建补偿: problemId={}, reason={}",
                    document.problemId(), describe(exception));
            return false;
        }
    }

    /**
     * 删除题目文档。
     *
     * @param problemId 题目标识
     */
    public void deleteDocument(Long problemId) {
        try {
            client.performRequest(new Request("DELETE",
                    "/" + properties.getIndexName() + "/_doc/" + problemId));
        } catch (IOException exception) {
            log.warn("题库索引删除失败，等待重建补偿: problemId={}, type={}",
                    problemId, exception.getClass().getSimpleName());
        }
    }

    /**
     * 重建索引并写入全量题目文档。
     *
     * @param documents 全量题目文档
     * @return 成功写入的文档数量
     */
    public int rebuild(List<ProblemSearchDocument> documents) {
        try {
            Request delete = new Request("DELETE", "/" + properties.getIndexName());
            client.performRequest(delete);
        } catch (IOException ignored) {
            // 索引不存在时直接创建
        }
        createIndex();
        int indexed = 0;
        for (ProblemSearchDocument document : documents) {
            if (indexDocument(document)) {
                indexed++;
            }
        }
        return indexed;
    }

    /**
     * 汇总索引操作失败的具体原因。
     *
     * @param exception 索引操作抛出的异常
     * @return 包含响应状态与响应体的可读描述
     */
    private String describe(IOException exception) {
        if (exception instanceof ResponseException responseException) {
            Response response = responseException.getResponse();
            return "status=" + response.getStatusLine().getStatusCode()
                    + ", body=" + responseBody(response);
        }
        return "type=" + exception.getClass().getSimpleName() + ", message=" + exception.getMessage();
    }

    /**
     * 读取失败响应的响应体，读取失败时返回空字符串。
     *
     * @param response 失败响应
     * @return 响应体文本
     */
    private String responseBody(Response response) {
        HttpEntity entity = response.getEntity();
        if (entity == null) return "";
        try {
            return EntityUtils.toString(entity);
        } catch (IOException ignored) {
            return "";
        }
    }

    private void createIndex() {
        try {
            Request request = new Request("PUT", "/" + properties.getIndexName());
            request.setJsonEntity(objectMapper.writeValueAsString(indexDefinition()));
            client.performRequest(request);
            log.info("题库索引已创建: index={}", properties.getIndexName());
        } catch (IOException exception) {
            log.warn("题库索引创建失败，全文检索将走数据库降级: type={}",
                    exception.getClass().getSimpleName());
        }
    }

    private Map<String, Object> indexDefinition() {
        Map<String, Object> propertiesMap = new LinkedHashMap<>();
        propertiesMap.put("problemId", Map.of("type", "long"));
        propertiesMap.put("code", Map.of("type", "integer"));
        propertiesMap.put("problemNumber", Map.of("type", "keyword"));
        propertiesMap.put("title", Map.of("type", "text", "analyzer", "cjk"));
        propertiesMap.put("content", Map.of("type", "text", "analyzer", "cjk"));
        propertiesMap.put("contestId", Map.of("type", "long"));
        propertiesMap.put("year", Map.of("type", "integer"));
        propertiesMap.put("difficulty", Map.of("type", "integer"));
        propertiesMap.put("statementLanguage", Map.of("type", "keyword"));
        propertiesMap.put("tagIds", Map.of("type", "long"));
        propertiesMap.put("status", Map.of("type", "integer"));
        // 平台统一以 yyyy-MM-dd HH:mm:ss 序列化时间，索引需显式兼容该格式
        propertiesMap.put("updateTime", Map.of("type", "date",
                "format", "yyyy-MM-dd HH:mm:ss||strict_date_optional_time||epoch_millis"));
        return Map.of("mappings", Map.of("properties", propertiesMap));
    }

    private Map<String, Object> toMap(ProblemSearchDocument document) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("problemId", document.problemId());
        values.put("code", document.code());
        values.put("problemNumber", document.problemNumber());
        values.put("title", document.title());
        values.put("content", document.content());
        values.put("contestId", document.contestId());
        values.put("year", document.year());
        values.put("difficulty", document.difficulty());
        values.put("statementLanguage", document.statementLanguage());
        values.put("tagIds", document.tagIds() == null ? List.of() : document.tagIds());
        values.put("status", document.status());
        values.put("updateTime", document.updateTime());
        return values;
    }
}
