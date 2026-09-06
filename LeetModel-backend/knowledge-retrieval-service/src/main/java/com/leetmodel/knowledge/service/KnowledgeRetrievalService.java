package com.leetmodel.knowledge.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
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
import com.leetmodel.knowledge.defense.CatalogSelectionDefenseEngine;
import com.leetmodel.knowledge.defense.DefensiveCatalogOutputParser;
import com.leetmodel.knowledge.defense.GracefulFallbackProvider;
import com.leetmodel.knowledge.defense.PathWhitelistValidator;
import com.leetmodel.knowledge.defense.SelectionCountTruncator;
import com.leetmodel.knowledge.defense.dto.DefenseResult;
import com.leetmodel.knowledge.manifest.YamlKnowledgeManifestLoader;
import com.leetmodel.knowledge.manifest.model.KnowledgeManifest;
import com.leetmodel.knowledge.manifest.model.ManifestDocument;
import com.leetmodel.knowledge.prompt.PromptTemplateRenderer;
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
    public static final String AI_CATALOG_TAG_V1 = "AI_CATALOG_TAG_V1";
    public static final String HYBRID_RETRIEVAL_V1 = "HYBRID_RETRIEVAL_V1";
    public static final String SUGGESTION_DEEP_RETRIEVAL_V1 = "SUGGESTION_DEEP_RETRIEVAL_V1";
    private static final Set<String> SUPPORTED = Set.of(
            VECTOR_RAG_V1, AI_DIRECTORY_V1, AI_CATALOG_TAG_V1, HYBRID_RETRIEVAL_V1, SUGGESTION_DEEP_RETRIEVAL_V1);

    private final KnowledgeRetrievalProperties properties;
    private final AiClient aiClient;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final YamlKnowledgeManifestLoader manifestLoader;
    private final CatalogSelectionDefenseEngine defenseEngine;

    public KnowledgeRetrievalService(KnowledgeRetrievalProperties properties, AiClient aiClient,
                                     RestClient restClient, ObjectMapper objectMapper) {
        this(properties, aiClient, restClient, objectMapper,
                new YamlKnowledgeManifestLoader(),
                new CatalogSelectionDefenseEngine(
                        new DefensiveCatalogOutputParser(),
                        new PathWhitelistValidator(),
                        new SelectionCountTruncator(),
                        new GracefulFallbackProvider()
                ));
    }

    @org.springframework.beans.factory.annotation.Autowired
    public KnowledgeRetrievalService(KnowledgeRetrievalProperties properties, AiClient aiClient,
                                     RestClient restClient, ObjectMapper objectMapper,
                                     YamlKnowledgeManifestLoader manifestLoader,
                                     CatalogSelectionDefenseEngine defenseEngine) {
        this.properties = properties;
        this.aiClient = aiClient;
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.manifestLoader = manifestLoader;
        this.defenseEngine = defenseEngine;
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
            case AI_DIRECTORY_V1, AI_CATALOG_TAG_V1 -> directory(runId, request, topK);
            case HYBRID_RETRIEVAL_V1, SUGGESTION_DEEP_RETRIEVAL_V1 -> hybrid(runId, request.getQuery(),
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
        int candidateK = Math.max(topK * 2, 10);
        RetrievalSnapshot vectorSnap;
        try {
            vectorSnap = vector(runId + ":vector", query, requiredIndexVersion, candidateK);
        } catch (Exception e) {
            log.warn("混合检索向量分支异常，降级为空: {}", e.getMessage());
            vectorSnap = new RetrievalSnapshot("VECTOR", requiredIndexVersion, null, null, List.of());
        }
        RetrievalSnapshot bm25Snap = bm25(runId + ":bm25", query, requiredIndexVersion, candidateK);

        Map<String, KnowledgeCitationDTO> candidates = new LinkedHashMap<>();
        Map<String, Double> rrfScores = new LinkedHashMap<>();
        double k = 60.0;

        List<KnowledgeCitationDTO> vectorHits = vectorSnap.citations();
        for (int rank = 0; rank < vectorHits.size(); rank++) {
            KnowledgeCitationDTO hit = vectorHits.get(rank);
            String key = hit.getDocumentId() + ":" + hit.getChunkId();
            candidates.put(key, hit);
            rrfScores.put(key, rrfScores.getOrDefault(key, 0.0) + (1.0 / (k + rank + 1)));
        }

        List<KnowledgeCitationDTO> bm25Hits = bm25Snap.citations();
        for (int rank = 0; rank < bm25Hits.size(); rank++) {
            KnowledgeCitationDTO hit = bm25Hits.get(rank);
            String key = hit.getDocumentId() + ":" + hit.getChunkId();
            candidates.putIfAbsent(key, hit);
            rrfScores.put(key, rrfScores.getOrDefault(key, 0.0) + (1.0 / (k + rank + 1)));
        }

        List<KnowledgeCitationDTO> fused = candidates.entrySet().stream()
                .sorted(Comparator.comparingDouble((Map.Entry<String, KnowledgeCitationDTO> e) -> rrfScores.getOrDefault(e.getKey(), 0.0)).reversed())
                .limit(topK)
                .map(e -> {
                    KnowledgeCitationDTO item = e.getValue();
                    double fusedScore = rrfScores.getOrDefault(e.getKey(), 0.0);
                    return new KnowledgeCitationDTO(item.getCitationId(), item.getDocumentId(), item.getChunkId(),
                            item.getTitle(), item.getSourcePath(), item.getSection(), item.getContentHash(),
                            item.getAuthorityLevel(), item.getApplicability(), fusedScore, item.getContent());
                })
                .toList();

        String indexVersion = vectorSnap.indexVersion() != null ? vectorSnap.indexVersion() : bm25Snap.indexVersion();
        return new RetrievalSnapshot("VECTOR+BM25_RRF", indexVersion, null, null, fused);
    }

    private RetrievalSnapshot bm25(String runId, String query, String requiredIndexVersion, int topK) {
        String indexName = requiredIndexVersion == null || requiredIndexVersion.isBlank()
                ? properties.getIndexAlias() : physicalIndexName(requiredIndexVersion);
        Map<String, Object> body = Map.of(
                "size", topK,
                "_source", List.of("chunkId", "documentId", "content", "sourcePath", "title",
                        "ragIndexVersion", "estimatedTokens", "contentHash"),
                "query", Map.of("multi_match", Map.of(
                        "query", query,
                        "fields", List.of("title^3", "content^1")
                ))
        );
        Request request = new Request("POST", "/" + indexName + "/_search");
        try {
            request.setJsonEntity(objectMapper.writeValueAsString(body));
            int timeout = Math.toIntExact(properties.getRequestTimeout().toMillis());
            request.setOptions(RequestOptions.DEFAULT.toBuilder().setRequestConfig(RequestConfig.custom()
                    .setConnectTimeout(timeout).setSocketTimeout(timeout)
                    .setConnectionRequestTimeout(timeout).build()));
            Response response = restClient.performRequest(request);
            JsonNode hits = objectMapper.readTree(EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8))
                    .path("hits").path("hits");
            List<KnowledgeCitationDTO> citations = new ArrayList<>();
            String actualVersion = requiredIndexVersion;
            for (JsonNode hit : hits) {
                double score = hit.path("_score").asDouble();
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
            return new RetrievalSnapshot("BM25", actualVersion, null, null, citations);
        } catch (Exception exception) {
            log.warn("Elasticsearch BM25 检索异常，降级为空: {}", exception.getMessage());
            return new RetrievalSnapshot("BM25", requiredIndexVersion, null, null, List.of());
        }
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
            JsonNode hits = objectMapper.readTree(EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8))
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

    private RetrievalSnapshot directory(String runId, KnowledgeRetrievalRequestDTO request, int topK) {
        Path root = Path.of(properties.getKnowledgeBasePath()).toAbsolutePath().normalize();
        KnowledgeManifest manifest = manifestLoader.loadRoot(root);
        Set<String> validPaths = manifest.getValidRelativePaths();

        String category = request.getCategory() != null ? request.getCategory() : "通用";
        String userQuery = request.getQuery() != null ? request.getQuery() : "";
        int maxSelection = Math.min(topK, properties.getDirectorySelectionLimit());

        // 1. 组装候选文档清单文本 (按分类相关度前置排序)
        List<ManifestDocument> allDocs = manifest.getAllDocuments();
        List<ManifestDocument> sortedDocs = sortDocumentsByCategory(allDocs, category);
        String manifestText = sortedDocs.stream()
                .limit(properties.getDirectoryCandidateLimit())
                .filter(doc -> !isUnsupportedProblemSpecific(doc.relativePath()))
                .map(doc -> doc.relativePath() + " | " + doc.title() + " | " + doc.summary())
                .reduce("", (left, right) -> left + right + "\n");

        // 2. 渲染 System 与 User 提示词模板
        String systemTemplate = PromptTemplateRenderer.loadClasspathPrompt("prompts/catalog-selection-system.st");
        String userTemplate = PromptTemplateRenderer.loadClasspathPrompt("prompts/catalog-selection-user.st");

        Map<String, String> sysVars = Map.of(
                "minSelection", "2",
                "maxSelection", String.valueOf(maxSelection)
        );
        Map<String, String> userVars = Map.of(
                "userQuery", userQuery,
                "category", category,
                "maxSelection", String.valueOf(maxSelection),
                "minSelection", "2",
                "catalogManifest", manifestText
        );

        String systemPrompt = PromptTemplateRenderer.render(systemTemplate, sysVars);
        String userPrompt = PromptTemplateRenderer.render(userTemplate, userVars);

        // 3. 调度模型并执行 4 道防线安全过滤
        DefenseResult defenseResult;
        try {
            AiChatRequest chatRequest = new AiChatRequest(
                    AiModality.TEXT,
                    context(runId, request.getWorkflowVersion(), "PROMPT_CATALOG_SELECT_V1",
                            "MODEL_CFG_KNOWLEDGE_DIRECTORY_0001"),
                    List.of(
                            message(AiRole.SYSTEM, systemPrompt),
                            message(AiRole.USER, userPrompt)
                    ),
                    1200,
                    0.0,
                    AiResponseFormat.JSON_OBJECT,
                    false
            );
            AiChatResponse chatResponse = aiClient.chat(chatRequest);
            if (chatResponse == null || chatResponse.content() == null || chatResponse.content().isBlank()) {
                defenseResult = defenseEngine.fallbackOnly(category, validPaths, "模型返回空响应");
            } else {
                defenseResult = defenseEngine.defend(chatResponse.content(), category, maxSelection, validPaths);
            }
        } catch (Exception e) {
            log.warn("选拔模型调用异常，直接转入防线4降级: runId={}, error={}", runId, e.getMessage());
            defenseResult = defenseEngine.fallbackOnly(category, validPaths, e.getMessage());
        }

        // 4. 整篇原子文档全量装配 (Document-as-a-Chunk) 与不可信边界包装
        List<KnowledgeCitationDTO> citations = new ArrayList<>();
        for (String selectedPath : defenseResult.selectedPaths()) {
            if (isUnsupportedProblemSpecific(selectedPath)) continue;
            Path filePath = root.resolve(selectedPath).normalize();
            if (!Files.isRegularFile(filePath)) {
                log.warn("选中的文档不存在于磁盘: {}", selectedPath);
                continue;
            }
            try {
                String rawContent = Files.readString(filePath, StandardCharsets.UTF_8);
                ManifestDocument doc = manifest.findByPath(selectedPath);
                String title = doc != null ? doc.title() : filePath.getFileName().toString().replaceFirst("\\.md$", "");
                String level = doc != null ? doc.authorityLevel() : authorityLevel(selectedPath);
                String wrappedContent = wrapUntrustedBoundary(rawContent, selectedPath, level);
                String documentId = hash(selectedPath);
                String contentHash = hash(rawContent);
                citations.add(citation(documentId, documentId + "-document", title,
                        selectedPath, contentHash, 1.0, wrappedContent));
            } catch (IOException e) {
                log.error("读取选中知识文档失败: path={}, error={}", selectedPath, e.getMessage());
            }
        }

        String sourceVersion = "SOURCE_" + hash(citations.stream()
                .map(KnowledgeCitationDTO::getContentHash).reduce("", String::concat));

        return new RetrievalSnapshot("DIRECTORY", null, manifest.getManifestVersion(), sourceVersion, citations);
    }

    private List<ManifestDocument> sortDocumentsByCategory(List<ManifestDocument> docs, String category) {
        if (category == null || category.isBlank() || "通用".equals(category)) {
            return docs;
        }
        String normCat = category.toLowerCase(Locale.ROOT);
        return docs.stream()
                .sorted(Comparator.comparingInt(doc -> {
                    boolean match = doc.compositeTags().stream()
                            .anyMatch(t -> t.toLowerCase(Locale.ROOT).contains(normCat))
                            || doc.directoryName().toLowerCase(Locale.ROOT).contains(normCat);
                    return match ? 0 : 1;
                }))
                .toList();
    }

    private String wrapUntrustedBoundary(String content, String sourcePath, String level) {
        return "<参考知识事实 来源=\"" + sourcePath + "\" 权威等级=\"" + level + "\">\n"
                + content + "\n"
                + "</参考知识事实>";
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
