package com.leetmodel.knowledge.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leetmodel.common.ai.client.AiClient;
import com.leetmodel.common.ai.model.AiCallContext;
import com.leetmodel.common.ai.model.AiCallPriority;
import com.leetmodel.common.ai.model.AiChatRequest;
import com.leetmodel.common.ai.model.AiChatResponse;
import com.leetmodel.common.ai.model.AiContentPart;
import com.leetmodel.common.ai.model.AiContentType;
import com.leetmodel.common.ai.model.AiEmbeddingRequest;
import com.leetmodel.common.ai.model.AiEmbeddingResponse;
import com.leetmodel.common.ai.model.AiFeatureCode;
import com.leetmodel.common.ai.model.AiMessage;
import com.leetmodel.common.ai.model.AiModality;
import com.leetmodel.common.ai.model.AiOperationCode;
import com.leetmodel.common.ai.model.AiResponseFormat;
import com.leetmodel.common.ai.model.AiRole;
import com.leetmodel.common.api.dto.KnowledgeCitationDTO;
import com.leetmodel.common.api.dto.KnowledgeRetrievalRequestDTO;
import com.leetmodel.common.api.dto.KnowledgeRetrievalResultDTO;
import com.leetmodel.knowledge.config.KnowledgeRetrievalProperties;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.util.EntityUtils;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 跨业务知识检索入口。每个已发布版本固定自己的执行分支，调用方必须显式选择版本。
 */
@Slf4j
@Service
public class KnowledgeRetrievalService {
    public static final String VECTOR_RAG_V1 = "VECTOR_RAG_V1";
    public static final String AI_DIRECTORY_V1 = "AI_DIRECTORY_V1";
    public static final String HYBRID_RETRIEVAL_V1 = "HYBRID_RETRIEVAL_V1";
    private static final Set<String> SUPPORTED = Set.of(
            VECTOR_RAG_V1, AI_DIRECTORY_V1, HYBRID_RETRIEVAL_V1);

    private final KnowledgeRetrievalProperties properties;
    private final AiClient aiClient;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public KnowledgeRetrievalService(KnowledgeRetrievalProperties properties, AiClient aiClient,
                                     RestClient restClient, ObjectMapper objectMapper) {
        this.properties = properties;
        this.aiClient = aiClient;
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    public KnowledgeRetrievalResultDTO retrieve(KnowledgeRetrievalRequestDTO request) {
        if (!SUPPORTED.contains(request.getWorkflowVersion())) {
            throw new IllegalArgumentException("未知知识检索版本: " + request.getWorkflowVersion());
        }
        String runId = UUID.randomUUID().toString();
        int topK = request.getTopK() == null ? properties.getTopK() : request.getTopK();
        int budget = request.getTokenBudget() == null
                ? properties.getTokenBudget() : request.getTokenBudget();
        RetrievalSnapshot snapshot = switch (request.getWorkflowVersion()) {
            case VECTOR_RAG_V1 -> vector(runId, request.getQuery(), request.getRequiredIndexVersion(), topK);
            case AI_DIRECTORY_V1 -> directory(runId, request.getQuery(), topK);
            case HYBRID_RETRIEVAL_V1 -> hybrid(runId, request.getQuery(),
                    request.getRequiredIndexVersion(), topK);
            default -> throw new IllegalStateException("未实现的知识检索版本");
        };
        List<KnowledgeCitationDTO> citations = applyBudget(snapshot.citations(), budget, topK);
        String status = citations.isEmpty() ? "NO_CONTEXT" : "COMPLETED";
        log.info("knowledge-retrieval status={} runId={} workflow={} branch={} citations={}",
                status, runId, request.getWorkflowVersion(), snapshot.branch(), citations.size());
        return new KnowledgeRetrievalResultDTO(runId, request.getWorkflowVersion(), snapshot.branch(),
                snapshot.indexVersion(), snapshot.manifestVersion(), snapshot.sourceVersion(),
                status, citations);
    }

    private RetrievalSnapshot hybrid(String runId, String query, String requiredIndexVersion, int topK) {
        RetrievalSnapshot vector = vector(runId + ":vector", query, requiredIndexVersion, topK);
        RetrievalSnapshot directory = directory(runId + ":directory", query, topK);
        Map<String, KnowledgeCitationDTO> combined = new LinkedHashMap<>();
        vector.citations().forEach(item -> combined.put(item.getDocumentId() + ":" + item.getChunkId(), item));
        directory.citations().forEach(item -> combined.putIfAbsent(
                item.getDocumentId() + ":" + item.getChunkId(), item));
        return new RetrievalSnapshot("VECTOR+DIRECTORY", vector.indexVersion(),
                directory.manifestVersion(), directory.sourceVersion(), List.copyOf(combined.values()));
    }

    private RetrievalSnapshot vector(String runId, String query, String requiredIndexVersion, int topK) {
        AiCallContext context = context(runId, VECTOR_RAG_V1, "PROMPT_NONE",
                "MODEL_CFG_RAG_V1");
        AiEmbeddingResponse embedding = aiClient.embed(AiEmbeddingRequest.single(
                "RAG_V1", context, query));
        if (embedding == null || embedding.dimension() != properties.getEmbeddingDimension()
                || embedding.vectors() == null || embedding.vectors().size() != 1) {
            throw new IllegalStateException("知识检索 Embedding 响应形状无效");
        }
        List<Float> vector = embedding.vectors().get(0).values();
        String indexName = requiredIndexVersion == null || requiredIndexVersion.isBlank()
                ? properties.getIndexAlias() : physicalIndexName(requiredIndexVersion);
        Map<String, Object> body = Map.of(
                "size", topK,
                "_source", List.of("chunkId", "documentId", "content", "sourcePath", "title",
                        "ragIndexVersion", "estimatedTokens", "contentHash"),
                "knn", Map.of("field", "embedding", "query_vector", vector,
                        "k", topK, "num_candidates", Math.max(100, topK * 10)));
        Request request = new Request("POST", "/" + indexName + "/_search");
        try {
            request.setJsonEntity(objectMapper.writeValueAsString(body));
            int timeout = Math.toIntExact(properties.getRequestTimeout().toMillis());
            request.setOptions(RequestOptions.DEFAULT.toBuilder().setRequestConfig(RequestConfig.custom()
                    .setConnectTimeout(timeout).setSocketTimeout(timeout)
                    .setConnectionRequestTimeout(timeout).build()));
            Response response = restClient.performRequest(request);
            JsonNode hits = objectMapper.readTree(EntityUtils.toString(response.getEntity()))
                    .path("hits").path("hits");
            List<KnowledgeCitationDTO> citations = new ArrayList<>();
            String actualVersion = requiredIndexVersion;
            for (JsonNode hit : hits) {
                double score = hit.path("_score").asDouble();
                if (score < properties.getScoreThreshold()) continue;
                JsonNode source = hit.path("_source");
                String hitVersion = source.path("ragIndexVersion").asText();
                if (requiredIndexVersion != null && !requiredIndexVersion.equals(hitVersion)) {
                    throw new IllegalStateException("检索结果不属于请求锁定的知识索引");
                }
                if (actualVersion == null) actualVersion = hitVersion;
                String sourcePath = source.path("sourcePath").asText();
                if (isUnsupportedProblemSpecific(sourcePath)) continue;
                String documentId = source.path("documentId").asText();
                String chunkId = source.path("chunkId").asText();
                citations.add(citation(documentId, chunkId, source.path("title").asText(),
                        sourcePath, source.path("contentHash").asText(), score,
                        source.path("content").asText()));
            }
            return new RetrievalSnapshot("VECTOR", actualVersion, null, null, citations);
        } catch (IOException exception) {
            throw new IllegalStateException("Elasticsearch 知识检索失败", exception);
        }
    }

    private RetrievalSnapshot directory(String runId, String query, int topK) {
        Path root = Path.of(properties.getKnowledgeBasePath()).toAbsolutePath().normalize();
        Path contentRoot = root.resolve("数学建模").normalize();
        if (!contentRoot.startsWith(root) || !Files.isDirectory(contentRoot)) {
            throw new IllegalStateException("受控知识库目录不可用");
        }
        List<DirectoryCandidate> candidates = loadCandidates(root, contentRoot);
        String manifestVersion = "MANIFEST_" + hash(candidates.stream()
                .map(item -> item.path() + "\n" + item.summary()).reduce("", String::concat));
        String manifest = candidates.stream().limit(properties.getDirectoryCandidateLimit())
                .map(item -> item.path() + " | " + item.title() + " | " + item.summary())
                .reduce("", (left, right) -> left + right + "\n");
        String prompt = """
                你是数学建模知识目录选择器。只能从给定清单选择与查询直接相关的文档。
                返回 JSON：{"paths":["受控相对路径"]}。最多选择 %d 项，不得编造路径。
                查询：%s
                文档清单：
                %s
                """.formatted(Math.min(topK, properties.getDirectorySelectionLimit()), query, manifest);
        AiChatResponse response = aiClient.chat(new AiChatRequest(AiModality.TEXT,
                context(runId, AI_DIRECTORY_V1, "PROMPT_AI_DIRECTORY_0001",
                        "MODEL_CFG_KNOWLEDGE_DIRECTORY_0001"),
                List.of(message(AiRole.USER, prompt)), 1200, 0.0,
                AiResponseFormat.JSON_OBJECT, false));
        if (response == null || response.content() == null || response.content().isBlank()) {
            throw new IllegalStateException("目录检索没有返回选择结果");
        }
        try {
            JsonNode selected = objectMapper.readTree(response.content()).path("paths");
            Map<String, DirectoryCandidate> byPath = new LinkedHashMap<>();
            candidates.forEach(item -> byPath.put(item.path(), item));
            List<KnowledgeCitationDTO> citations = new ArrayList<>();
            for (JsonNode pathNode : selected) {
                DirectoryCandidate candidate = byPath.get(pathNode.asText());
                if (candidate == null || citations.size() >= topK) continue;
                Path file = root.resolve(candidate.path()).normalize();
                if (!file.startsWith(contentRoot) || !Files.isRegularFile(file)) continue;
                String content = Files.readString(file, StandardCharsets.UTF_8);
                String documentId = hash(candidate.path());
                citations.add(citation(documentId, documentId + "-document", candidate.title(),
                        candidate.path(), hash(content), 1.0, content));
            }
            String sourceVersion = "SOURCE_" + hash(citations.stream()
                    .map(KnowledgeCitationDTO::getContentHash).reduce("", String::concat));
            return new RetrievalSnapshot("DIRECTORY", null, manifestVersion, sourceVersion, citations);
        } catch (IOException exception) {
            throw new IllegalStateException("目录检索输出无法读取", exception);
        }
    }

    private List<DirectoryCandidate> loadCandidates(Path root, Path contentRoot) {
        try (var paths = Files.walk(contentRoot)) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".md"))
                    .filter(path -> !"README.md".equals(path.getFileName().toString()))
                    .filter(path -> !isUnsupportedProblemSpecific(
                            root.relativize(path).toString().replace('\\', '/')))
                    .sorted(Comparator.comparing(Path::toString))
                    .map(path -> candidate(root, path))
                    .toList();
        } catch (IOException exception) {
            throw new IllegalStateException("知识目录清单读取失败", exception);
        }
    }

    private DirectoryCandidate candidate(Path root, Path path) {
        try {
            String text = Files.readString(path, StandardCharsets.UTF_8);
            String title = path.getFileName().toString().replaceFirst("\\.md$", "");
            String summary = frontMatter(text, "summary");
            if (summary == null) summary = firstMeaningfulLine(text);
            return new DirectoryCandidate(root.relativize(path).toString().replace('\\', '/'),
                    title, limit(summary, 260));
        } catch (IOException exception) {
            throw new IllegalStateException("知识目录元数据读取失败", exception);
        }
    }

    private KnowledgeCitationDTO citation(String documentId, String chunkId, String title,
                                           String sourcePath, String contentHash, double score,
                                           String content) {
        String level = authorityLevel(sourcePath);
        String citationId = "KC-" + hash(documentId + ":" + chunkId).substring(0, 16);
        return new KnowledgeCitationDTO(citationId, documentId, chunkId, title, sourcePath,
                null, contentHash == null || contentHash.isBlank() ? hash(content) : contentHash,
                level, "L5".equals(level) ? "AUXILIARY_ONLY" : "GENERAL_MODELING",
                score, content);
    }

    private List<KnowledgeCitationDTO> applyBudget(List<KnowledgeCitationDTO> input,
                                                    int tokenBudget, int topK) {
        List<KnowledgeCitationDTO> result = new ArrayList<>();
        int used = 0;
        for (KnowledgeCitationDTO item : input) {
            if (result.size() >= topK) break;
            int tokens = Math.max(1, item.getContent().length() / 2);
            if (used + tokens > tokenBudget) {
                int remainingChars = Math.max(0, (tokenBudget - used) * 2);
                if (remainingChars < 200) continue;
                item.setContent(limit(item.getContent(), remainingChars));
                tokens = Math.max(1, item.getContent().length() / 2);
            }
            used += tokens;
            result.add(item);
        }
        return List.copyOf(result);
    }

    private String authorityLevel(String sourcePath) {
        String normalized = sourcePath == null ? "" : sourcePath;
        if (normalized.contains("论文评审/评审板块/")
                || normalized.contains("论文评审/评审视角/")
                || normalized.contains("论文评审/官方规范与讲评/")) return "L3";
        if (normalized.contains("题型方法/") || normalized.contains("模型方法/")) return "L4";
        return "L5";
    }

    /** 缺少赛事、年份和题号元数据时，题目专属细则不得进入跨题检索结果。 */
    private boolean isUnsupportedProblemSpecific(String sourcePath) {
        String normalized = sourcePath == null ? "" : sourcePath;
        if (normalized.contains("论文评审/阅卷标准/")) return true;
        return normalized.contains("论文评审/官方规范与讲评/")
                && normalized.matches(".*[A-FＡ-Ｆ]题.*");
    }

    private String physicalIndexName(String version) {
        String normalized = version.toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z0-9][a-z0-9_-]{2,127}")) {
            throw new IllegalArgumentException("知识索引版本非法");
        }
        return properties.getIndexAlias().replaceFirst("-read$", "") + "-" + normalized;
    }

    private AiCallContext context(String runId, String workflowVersion, String promptVersion,
                                  String modelConfigVersion) {
        return new AiCallContext("knowledge-retrieval-service", AiFeatureCode.RAG,
                AiOperationCode.RETRIEVE_CONTEXT, "retrieval:" + runId, workflowVersion,
                promptVersion, modelConfigVersion, null, AiCallPriority.P1,
                "knowledge-retrieval:" + runId, Instant.now().plusSeconds(180));
    }

    private AiMessage message(AiRole role, String text) {
        return new AiMessage(role, List.of(new AiContentPart(AiContentType.TEXT, text, null)));
    }

    private String frontMatter(String text, String field) {
        if (text == null || !text.startsWith("---")) return null;
        int end = text.indexOf("\n---", 3);
        if (end < 0) return null;
        for (String line : text.substring(3, end).split("\\R")) {
            int colon = line.indexOf(':');
            if (colon > 0 && field.equals(line.substring(0, colon).trim())) {
                return line.substring(colon + 1).trim();
            }
        }
        return null;
    }

    private String firstMeaningfulLine(String text) {
        for (String line : text.split("\\R")) {
            String value = line.replaceFirst("^#+\\s*", "").trim();
            if (!value.isBlank() && !"---".equals(value) && !value.contains(":")) return value;
        }
        return "数学建模知识文档";
    }

    private String limit(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, max);
    }

    private String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (Exception exception) {
            throw new IllegalStateException("无法生成知识版本摘要", exception);
        }
    }

    private record DirectoryCandidate(String path, String title, String summary) {}

    private record RetrievalSnapshot(String branch, String indexVersion, String manifestVersion,
                                     String sourceVersion, List<KnowledgeCitationDTO> citations) {}
}
