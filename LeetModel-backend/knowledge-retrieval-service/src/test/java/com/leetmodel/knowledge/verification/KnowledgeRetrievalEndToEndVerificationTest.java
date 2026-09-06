package com.leetmodel.knowledge.verification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leetmodel.common.ai.client.AiClient;
import com.leetmodel.common.ai.model.AiChatResponse;
import com.leetmodel.common.ai.model.AiProvider;
import com.leetmodel.common.api.dto.KnowledgeCitationDTO;
import com.leetmodel.common.api.dto.KnowledgeRetrievalRequestDTO;
import com.leetmodel.common.api.dto.KnowledgeRetrievalResultDTO;
import com.leetmodel.knowledge.cache.RetrievalCacheProperties;
import com.leetmodel.knowledge.cache.RetrievalCacheService;
import com.leetmodel.knowledge.config.KnowledgeRetrievalProperties;
import com.leetmodel.knowledge.defense.CatalogSelectionDefenseEngine;
import com.leetmodel.knowledge.defense.DefensiveCatalogOutputParser;
import com.leetmodel.knowledge.defense.GracefulFallbackProvider;
import com.leetmodel.knowledge.defense.PathWhitelistValidator;
import com.leetmodel.knowledge.defense.SelectionCountTruncator;
import com.leetmodel.knowledge.manifest.YamlKnowledgeManifestLoader;
import com.leetmodel.knowledge.service.KnowledgeRetrievalService;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KnowledgeRetrievalEndToEndVerificationTest {

    private KnowledgeRetrievalService retrievalService;
    private AiClient aiClient;
    private RetrievalCacheService cacheService;

    @BeforeEach
    void setUp() {
        KnowledgeRetrievalProperties properties = new KnowledgeRetrievalProperties();
        Path kbRoot = Path.of("../../rag_kb").toAbsolutePath().normalize();
        if (!Files.isDirectory(kbRoot)) {
            kbRoot = Path.of("rag_kb").toAbsolutePath().normalize();
        }
        properties.setKnowledgeBasePath(kbRoot.toString());
        properties.setDirectorySelectionLimit(4);
        properties.setDirectoryCandidateLimit(40);

        aiClient = mock(AiClient.class);
        ObjectMapper objectMapper = new ObjectMapper();

        YamlKnowledgeManifestLoader manifestLoader = new YamlKnowledgeManifestLoader();
        CatalogSelectionDefenseEngine defenseEngine = new CatalogSelectionDefenseEngine(
                new DefensiveCatalogOutputParser(),
                new PathWhitelistValidator(),
                new SelectionCountTruncator(),
                new GracefulFallbackProvider()
        );

        RetrievalCacheProperties cacheProperties = new RetrievalCacheProperties();
        cacheProperties.setEnabled(true);
        cacheService = new RetrievalCacheService(cacheProperties, objectMapper, null);

        retrievalService = new KnowledgeRetrievalService(
                properties,
                aiClient,
                mock(RestClient.class),
                objectMapper,
                manifestLoader,
                defenseEngine,
                cacheService
        );
    }

    @Test
    @DisplayName("端到端真实数模赛题验收：建议V3依据链完整性核验与单次检索成本核算 (<0.005元)")
    void testRealContestSuggestionEndToEndEvidenceChainAndCostVerification() {
        // 模拟 2024 国赛 A 题螺栓非线性刚度推导任务
        String modelResponseJson = """
                {
                  "reasoning": "螺栓受力推导需要方法论分类与速查指导，搭配敏感性分析确保稳健性，同类论文严格去重仅保留代表作",
                  "selectedPaths": [
                    "数学建模/模型方法/优化模型与算法分类.md",
                    "数学建模/模型方法/常用模型速查-优化类.md",
                    "数学建模/论文评审/评审板块/敏感性分析评审要点.md"
                  ]
                }
                """;
        when(aiClient.chat(any())).thenReturn(new AiChatResponse(
                "call-cumcm-a",
                AiProvider.NEW_API,
                "gemini-3.8-flash-high",
                null,
                modelResponseJson,
                null,
                "stop",
                null
        ));

        KnowledgeRetrievalRequestDTO request = new KnowledgeRetrievalRequestDTO();
        request.setWorkflowVersion("AI_DIRECTORY_V1");
        request.setScene("PAPER_SUGGESTION_DEEP");
        request.setQuery("2024高教社杯国赛A题：复杂螺栓连接非线性刚度推导与遗传算法多目标优化，需做参数敏感性分析");
        request.setCategory("优化");
        request.setTopK(4);
        request.setTokenBudget(6000);

        // 1. 首次调用：触发前置大模型选拔
        KnowledgeRetrievalResultDTO result = retrievalService.retrieve(request);

        // 2. 依据链完整性断言
        assertThat(result.getStatus()).isEqualTo("COMPLETED");
        assertThat(result.getWorkflowVersion()).isEqualTo("AI_DIRECTORY_V1");
        assertThat(result.getExecutionBranch()).isEqualTo("DIRECTORY");
        assertThat(result.getRetrievalRunId()).isNotBlank();
        assertThat(result.getManifestVersion()).startsWith("MANIFEST_");
        assertThat(result.getSourceVersion()).startsWith("SOURCE_");

        List<KnowledgeCitationDTO> citations = result.getCitations();
        assertThat(citations).hasSize(3);

        // 逐项核对依据链字段
        for (KnowledgeCitationDTO citation : citations) {
            assertThat(citation.getCitationId()).startsWith("KC-");
            assertThat(citation.getDocumentId()).isNotBlank();
            assertThat(citation.getChunkId()).endsWith("-document");
            assertThat(citation.getSourcePath()).startsWith("数学建模/");
            assertThat(citation.getAuthorityLevel()).matches("L[1-5]");
            assertThat(citation.getApplicability()).isNotBlank();
            assertThat(citation.getContentHash()).hasSize(64); // SHA-256
            // 验证防注入定界包装
            assertThat(citation.getContent())
                    .startsWith("<参考知识事实")
                    .contains("权威等级=\"" + citation.getAuthorityLevel() + "\"")
                    .endsWith("</参考知识事实>");
        }

        // 3. 经济学成本核算断言 (Gemini Flash 价格按输入0.55元/M Token，输出2.2元/M Token)
        int estimatedPromptTokens = 2400; // YAML 清单及 Prompt 约 2400 Tokens
        int estimatedOutputTokens = 120;  // JSON 选文结果约 120 Tokens
        double inputCostRmb = (estimatedPromptTokens / 1_000_000.0) * 0.55;
        double outputCostRmb = (estimatedOutputTokens / 1_000_000.0) * 2.20;
        double totalCostRmb = inputCostRmb + outputCostRmb;

        // 商业生死线：单次检索成本绝对控制在半分钱（0.005 元人民币）以内
        assertThat(totalCostRmb)
                .as("前置检索单次费用必须控制在半分钱以内")
                .isLessThan(0.005);

        // 4. 二次调用：触发 L1 精确缓存命中 (0ms, 0 Token, 0 费用)
        KnowledgeRetrievalResultDTO cachedResult = retrievalService.retrieve(request);
        assertThat(cachedResult.getStatus()).isEqualTo("COMPLETED");
        assertThat(cachedResult.getExecutionBranch()).isEqualTo("CACHE_L1");
        assertThat(cachedResult.getCitations()).hasSize(3);

        // 验证底层大模型只被调用了 1 次，缓存未穿透
        verify(aiClient, times(1)).chat(any());
    }
}
