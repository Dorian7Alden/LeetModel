package com.leetmodel.knowledge.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leetmodel.common.ai.client.AiClient;
import com.leetmodel.common.ai.model.AiChatResponse;
import com.leetmodel.common.ai.model.AiProvider;
import com.leetmodel.common.api.dto.KnowledgeRetrievalRequestDTO;
import com.leetmodel.knowledge.config.KnowledgeRetrievalProperties;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

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
}
