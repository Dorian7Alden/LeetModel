package com.leetmodel.knowledge.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leetmodel.common.ai.client.AiClient;
import com.leetmodel.common.ai.model.AiChatResponse;
import com.leetmodel.common.ai.model.AiEmbeddingResponse;
import com.leetmodel.common.ai.model.AiEmbeddingVector;
import com.leetmodel.common.ai.model.AiProvider;
import com.leetmodel.common.api.dto.KnowledgeRetrievalRequestDTO;
import com.leetmodel.knowledge.config.KnowledgeRetrievalProperties;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KnowledgeRetrievalServiceTest {
    @TempDir Path tempDir;

    @Test
    void directoryVersionOnlyReturnsValidatedControlledPathsAndSnapshots() throws Exception {
        Path file = tempDir.resolve("数学建模/论文评审/评审板块/验证方法.md");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "---\nsummary: 参数扰动与基线对比的验证方法\n---\n# 验证方法\n应报告扰动范围与结果变化。");
        KnowledgeRetrievalProperties properties = new KnowledgeRetrievalProperties();
        properties.setKnowledgeBasePath(tempDir.toString());
        AiClient aiClient = mock(AiClient.class);
        when(aiClient.chat(any())).thenReturn(new AiChatResponse("call", AiProvider.NEW_API,
                "model", null, "{\"paths\":[\"数学建模/论文评审/评审板块/验证方法.md\"]}",
                null, "stop", null));
        KnowledgeRetrievalService service = new KnowledgeRetrievalService(properties, aiClient,
                mock(RestClient.class), new ObjectMapper());
        KnowledgeRetrievalRequestDTO request = new KnowledgeRetrievalRequestDTO();
        request.setWorkflowVersion("AI_DIRECTORY_V1");
        request.setQuery("优化模型如何做敏感性验证");
        request.setTopK(5);
        request.setTokenBudget(1000);

        var result = service.retrieve(request);

        assertThat(result.getStatus()).isEqualTo("COMPLETED");
        assertThat(result.getManifestVersion()).startsWith("MANIFEST_");
        assertThat(result.getSourceVersion()).startsWith("SOURCE_");
        assertThat(result.getCitations()).hasSize(1);
        assertThat(result.getCitations().get(0).getSourcePath())
                .isEqualTo("数学建模/论文评审/评审板块/验证方法.md");
        assertThat(result.getCitations().get(0).getAuthorityLevel()).isEqualTo("L3");
    }

    @Test
    void problemSpecificRubricIsExcludedWithoutApplicabilityMetadata() throws Exception {
        Path file = tempDir.resolve("数学建模/论文评审/阅卷标准/其他题评分细则.md");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "---\nsummary: 某一道题的专属评分细则\n---\n# 评分细则\n仅适用于原题。");
        KnowledgeRetrievalProperties properties = new KnowledgeRetrievalProperties();
        properties.setKnowledgeBasePath(tempDir.toString());
        AiClient aiClient = mock(AiClient.class);
        when(aiClient.chat(any())).thenReturn(new AiChatResponse("call", AiProvider.NEW_API,
                "model", null,
                "{\"paths\":[\"数学建模/论文评审/阅卷标准/其他题评分细则.md\"]}",
                null, "stop", null));
        KnowledgeRetrievalService service = new KnowledgeRetrievalService(properties, aiClient,
                mock(RestClient.class), new ObjectMapper());
        KnowledgeRetrievalRequestDTO request = new KnowledgeRetrievalRequestDTO();
        request.setWorkflowVersion("AI_DIRECTORY_V1");
        request.setQuery("当前论文如何改进");

        var result = service.retrieve(request);

        assertThat(result.getStatus()).isEqualTo("NO_CONTEXT");
        assertThat(result.getCitations()).isEmpty();
    }

    @Test
    void aiDirectoryWorkflowWithFullMarkdownAssemblyAndSecurityBoundaries() throws Exception {
        KnowledgeRetrievalProperties properties = new KnowledgeRetrievalProperties();
        Path kbRoot = Path.of("../../rag_kb").toAbsolutePath().normalize();
        if (!Files.isDirectory(kbRoot)) {
            kbRoot = Path.of("rag_kb").toAbsolutePath().normalize();
        }
        properties.setKnowledgeBasePath(kbRoot.toString());

        AiClient aiClient = mock(AiClient.class);
        String modelJson = """
                {
                  "reasoning": "根据优化建模需求挑选模型分类与算法速查",
                  "selectedPaths": [
                    "数学建模/模型方法/优化模型与算法分类.md",
                    "数学建模/模型方法/常用模型速查-优化类.md"
                  ]
                }
                """;
        when(aiClient.chat(any())).thenReturn(new AiChatResponse("call-ai", AiProvider.NEW_API,
                "gemini-3.8-flash-high", null, modelJson, null, "stop", null));

        KnowledgeRetrievalService service = new KnowledgeRetrievalService(properties, aiClient,
                mock(RestClient.class), new ObjectMapper());

        KnowledgeRetrievalRequestDTO request = new KnowledgeRetrievalRequestDTO();
        request.setWorkflowVersion("AI_DIRECTORY_V1");
        request.setQuery("复杂螺栓非线性优化求解");
        request.setCategory("优化");
        request.setTopK(4);
        request.setTokenBudget(5000);

        var result = service.retrieve(request);

        assertThat(result.getStatus()).isEqualTo("COMPLETED");
        assertThat(result.getExecutionBranch()).isEqualTo("DIRECTORY");
        assertThat(result.getCitations()).hasSize(2);

        var firstCitation = result.getCitations().get(0);
        assertThat(firstCitation.getContent())
                .startsWith("<参考知识事实 来源=\"数学建模/模型方法/优化模型与算法分类.md\" 权威等级=\"L4\">")
                .endsWith("</参考知识事实>");
        assertThat(firstCitation.getContent()).contains("优化模型与算法");
        assertThat(firstCitation.getAuthorityLevel()).isEqualTo("L4");
    }

    @Test
    void aiCatalogTagWorkflowGracefullyFallsBackOnGatewayException() {
        KnowledgeRetrievalProperties properties = new KnowledgeRetrievalProperties();
        Path kbRoot = Path.of("../../rag_kb").toAbsolutePath().normalize();
        if (!Files.isDirectory(kbRoot)) {
            kbRoot = Path.of("rag_kb").toAbsolutePath().normalize();
        }
        properties.setKnowledgeBasePath(kbRoot.toString());

        AiClient aiClient = mock(AiClient.class);
        when(aiClient.chat(any())).thenThrow(new RuntimeException("New-API Gateway Timeout 504"));

        KnowledgeRetrievalService service = new KnowledgeRetrievalService(properties, aiClient,
                mock(RestClient.class), new ObjectMapper());

        KnowledgeRetrievalRequestDTO request = new KnowledgeRetrievalRequestDTO();
        request.setWorkflowVersion("AI_CATALOG_TAG_V1");
        request.setQuery("预测未来30天趋势");
        request.setCategory("预测");
        request.setTopK(3);
        request.setTokenBudget(4000);

        var result = service.retrieve(request);

        assertThat(result.getStatus()).isEqualTo("COMPLETED");
        assertThat(result.getCitations()).isNotEmpty();
        assertThat(result.getCitations().get(0).getContent())
                .startsWith("<参考知识事实 来源=\"")
                .endsWith("</参考知识事实>");
        assertThat(result.getCitations().get(0).getSourcePath()).contains("预测");
    }

    @Test
    void hybridRetrievalCombinesVectorAndBm25WithRrfRanking() throws Exception {
        KnowledgeRetrievalProperties properties = new KnowledgeRetrievalProperties();
        properties.setEmbeddingDimension(1024);
        AiClient aiClient = mock(AiClient.class);
        List<Float> dummyVector = java.util.Collections.nCopies(1024, 0.1f);
        when(aiClient.embed(any())).thenReturn(new AiEmbeddingResponse("call",
                "RAG_V1", "qwen3.7-text-embedding", 1024,
                List.of(new AiEmbeddingVector(0, dummyVector)), null, null));

        RestClient restClient = mock(RestClient.class);
        org.apache.http.HttpEntity entity = mock(org.apache.http.HttpEntity.class);
        org.elasticsearch.client.Response response = mock(org.elasticsearch.client.Response.class);
        when(response.getEntity()).thenReturn(entity);
        when(entity.getContent()).thenReturn(new java.io.ByteArrayInputStream("""
                {
                  "hits": {
                    "hits": [
                      {
                        "_score": 1.5,
                        "_source": {
                          "chunkId": "chunk-ahp",
                          "documentId": "doc-ahp",
                          "title": "层次分析法AHP",
                          "sourcePath": "题型方法/AHP.md",
                          "ragIndexVersion": "leetmodel-rag-v1",
                          "contentHash": "hash1",
                          "content": "AHP 层次分析法常用于权重计算"
                        }
                      }
                    ]
                  }
                }
                """.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        when(restClient.performRequest(any())).thenReturn(response);

        KnowledgeRetrievalService service = new KnowledgeRetrievalService(properties, aiClient,
                restClient, new ObjectMapper());
        KnowledgeRetrievalRequestDTO request = new KnowledgeRetrievalRequestDTO();
        request.setWorkflowVersion("HYBRID_RETRIEVAL_V1");
        request.setQuery("AHP 层次分析法");
        request.setTopK(5);

        var result = service.retrieve(request);

        assertThat(result.getStatus()).isEqualTo("COMPLETED");
        assertThat(result.getExecutionBranch()).isEqualTo("VECTOR+BM25_RRF");
        assertThat(result.getCitations()).hasSize(1);
        assertThat(result.getCitations().get(0).getTitle()).contains("AHP");
    }

    @Test
    void suggestionDeepRetrievalCombinesHybridRrfForSubProblem() throws Exception {
        KnowledgeRetrievalProperties properties = new KnowledgeRetrievalProperties();
        properties.setEmbeddingDimension(1024);
        AiClient aiClient = mock(AiClient.class);
        List<Float> dummyVector = java.util.Collections.nCopies(1024, 0.2f);
        when(aiClient.embed(any())).thenReturn(new AiEmbeddingResponse("call-sug",
                "RAG_V1", "qwen3.7-text-embedding", 1024,
                List.of(new AiEmbeddingVector(0, dummyVector)), null, null));

        RestClient restClient = mock(RestClient.class);
        org.apache.http.HttpEntity entity = mock(org.apache.http.HttpEntity.class);
        org.elasticsearch.client.Response response = mock(org.elasticsearch.client.Response.class);
        when(response.getEntity()).thenReturn(entity);
        when(entity.getContent()).thenReturn(new java.io.ByteArrayInputStream("""
                {
                  "hits": {
                    "hits": [
                      {
                        "_score": 2.8,
                        "_source": {
                          "chunkId": "chunk-opt-01",
                          "documentId": "doc-opt",
                          "title": "常用模型速查-优化类",
                          "sourcePath": "模型方法/常用模型速查-优化类.md",
                          "ragIndexVersion": "leetmodel-rag-v1",
                          "contentHash": "hash-opt",
                          "content": "非线性整数规划松弛与分支定界求解"
                        }
                      }
                    ]
                  }
                }
                """.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        when(restClient.performRequest(any())).thenReturn(response);

        KnowledgeRetrievalService service = new KnowledgeRetrievalService(properties, aiClient,
                restClient, new ObjectMapper());
        KnowledgeRetrievalRequestDTO request = new KnowledgeRetrievalRequestDTO();
        request.setWorkflowVersion("SUGGESTION_DEEP_RETRIEVAL_V1");
        request.setScene("PAPER_SUGGESTION_DEEP");
        request.setQuery("OPTIMIZATION: 非线性整数规划 求解器收敛");
        request.setTopK(6);
        request.setTokenBudget(4000);

        var result = service.retrieve(request);

        assertThat(result.getStatus()).isEqualTo("COMPLETED");
        assertThat(result.getWorkflowVersion()).isEqualTo("SUGGESTION_DEEP_RETRIEVAL_V1");
        assertThat(result.getExecutionBranch()).isEqualTo("VECTOR+BM25_RRF");
        assertThat(result.getCitations()).hasSize(1);
        assertThat(result.getCitations().get(0).getTitle()).contains("优化类");
        assertThat(result.getCitations().get(0).getAuthorityLevel()).isEqualTo("L4");
    }
}
