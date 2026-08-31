package com.leetmodel.assistant.tool.knowledge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leetmodel.assistant.rag.workflow.RagWorkflowContext;
import com.leetmodel.assistant.rag.workflow.RagWorkflowContextProvider;
import com.leetmodel.assistant.tool.AssistantToolException;
import com.leetmodel.assistant.tool.AssistantToolExecutionContext;
import com.leetmodel.assistant.tool.AssistantToolOutput;
import com.leetmodel.assistant.tool.AssistantToolRegistry;
import com.leetmodel.assistant.workflow.AssistantProductionSnapshot;
import com.leetmodel.common.ai.client.AiClient;
import com.leetmodel.common.ai.model.AiChatRequest;
import com.leetmodel.common.ai.model.AiChatResponse;
import com.leetmodel.common.ai.model.AiOperationCode;
import com.leetmodel.common.ai.model.AiProvider;
import com.leetmodel.common.ai.model.AiToolCall;
import com.leetmodel.common.ai.model.AiToolChoiceType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExplainModelingKnowledgeToolTest {

    @Mock
    private AiClient aiClient;
    @Mock
    private RagWorkflowContextProvider ragProvider;

    private ObjectMapper objectMapper;
    private ExplainModelingKnowledgeTool tool;

    @BeforeEach
    void setUp() throws Exception {
        objectMapper = new ObjectMapper();
        tool = new ExplainModelingKnowledgeTool(aiClient, ragProvider, objectMapper);
    }

    @Test
    void noRagUsesDedicatedPromptDisablesToolsAndCapsVisibleAnswer() throws Exception {
        when(aiClient.chat(any())).thenReturn(response("概".repeat(350), List.of()));

        AssistantToolOutput output = tool.execute(new ExplainModelingKnowledgeInput(
                "什么是层次分析法", null, "判断矩阵"), context("NONE", null));

        ArgumentCaptor<AiChatRequest> captor = ArgumentCaptor.forClass(AiChatRequest.class);
        verify(aiClient).chat(captor.capture());
        AiChatRequest request = captor.getValue();
        assertThat(request.context().operationCode())
                .isEqualTo(AiOperationCode.EXPLAIN_MODELING_KNOWLEDGE);
        assertThat(request.context().promptVersion())
                .isEqualTo(ExplainModelingKnowledgeTool.PROMPT_VERSION);
        assertThat(request.context().modelExecutionConfigVersion())
                .isEqualTo(ExplainModelingKnowledgeTool.MODEL_CONFIG_VERSION);
        assertThat(request.context().idempotencyKey())
                .isEqualTo("assistant:12:attempt:1:tool:1");
        assertThat(request.tools()).isEmpty();
        assertThat(request.toolChoice().type()).isEqualTo(AiToolChoiceType.NONE);
        assertThat(request.messages()).hasSize(2);
        assertThat(request.messages().get(0).content().get(0).text())
                .contains("数学建模讲解员", "OUT_OF_SCOPE", "三百个中文字符");
        assertThat(request.messages().get(1).content().get(0).text())
                .contains("BEGIN_UNTRUSTED_USER_QUESTION", "BEGINNER", "判断矩阵");
        assertThat(output.terminalResponse().content().codePointCount(
                0, output.terminalResponse().content().length())).isEqualTo(300);
        assertThat(output.auditSnapshotJson()).doesNotContain("概概概");
        JsonNode audit = objectMapper.readTree(output.auditSnapshotJson());
        assertThat(audit.path("answerCodePoints").asInt()).isEqualTo(300);
        assertThat(audit.path("answerSha256").asText()).hasSize(64);
        verify(ragProvider, never()).retrieveExact(any(), any());
    }

    @Test
    void fixedRagUsesOnlySnapshotPhysicalIndex() {
        when(ragProvider.retrieveExact("线性规划 单纯形法", "rag-physical-v1"))
                .thenReturn(new RagWorkflowContext("BEGIN_UNTRUSTED_RAG_KNOWLEDGE_1\n参考资料\nEND",
                        "rag-physical-v1", 1, List.of("模型方法.md")));
        when(aiClient.chat(any())).thenReturn(response("线性规划是在约束下优化线性目标。", List.of()));

        AssistantToolOutput output = tool.execute(new ExplainModelingKnowledgeInput(
                "线性规划", KnowledgeLevel.INTERMEDIATE, "单纯形法"),
                context("FIXED_INDEX", "rag-physical-v1"));

        ArgumentCaptor<AiChatRequest> captor = ArgumentCaptor.forClass(AiChatRequest.class);
        verify(aiClient).chat(captor.capture());
        assertThat(captor.getValue().messages()).hasSize(3);
        assertThat(captor.getValue().messages().get(1).content().get(0).text())
                .contains("UNTRUSTED_RAG_KNOWLEDGE", "参考资料");
        assertThat(captor.getValue().context().ragIndexVersion()).isEqualTo("rag-physical-v1");
        assertThat(output.auditSnapshotJson())
                .contains("rag-physical-v1", "retrievedChunkCount");
    }

    @Test
    void outOfScopeMarkerBecomesFixedPlatformRangeReply() {
        when(aiClient.chat(any())).thenReturn(response(" OUT_OF_SCOPE ", List.of()));

        AssistantToolOutput output = tool.execute(new ExplainModelingKnowledgeInput(
                "今天股票买什么", KnowledgeLevel.BEGINNER, null), context("NONE", null));

        assertThat(output.terminalResponse().content())
                .isEqualTo("我只解答 LeetModel 平台使用和数学建模学习相关问题。");
        assertThat(output.auditSnapshotJson()).contains("\"outOfScope\":true");
    }

    @Test
    void rejectsRecursiveToolCallAndInvalidRagSnapshot() {
        when(aiClient.chat(any())).thenReturn(response(null,
                List.of(new AiToolCall("nested", "search_problem", "{}"))));

        assertThatThrownBy(() -> tool.execute(new ExplainModelingKnowledgeInput(
                "层次分析法", null, null), context("NONE", null)))
                .isInstanceOf(AssistantToolException.class)
                .extracting("errorCode").isEqualTo("TOOL_RECURSION_FORBIDDEN");
        assertThatThrownBy(() -> tool.execute(new ExplainModelingKnowledgeInput(
                "层次分析法", null, null), context("FIXED_INDEX", null)))
                .isInstanceOf(AssistantToolException.class)
                .extracting("errorCode").isEqualTo("KNOWLEDGE_RAG_SNAPSHOT_INVALID");
    }

    private AssistantToolExecutionContext context(String ragMode, String ragIndexVersion) {
        AssistantProductionSnapshot snapshot = new AssistantProductionSnapshot(
                "CFG", 1, "ASSISTANT_TOOLS_NO_RAG_V1", "PROMPT_MAIN", "MODEL_MAIN",
                ragMode, ragIndexVersion);
        return new AssistantToolExecutionContext(7L, 10L, 11L, 12L, 1, 1,
                AssistantToolRegistry.TOOLSET_V1, snapshot, Instant.now().plusSeconds(240));
    }

    private AiChatResponse response(String content, List<AiToolCall> calls) {
        return new AiChatResponse("nested-call", AiProvider.NEW_API, "model-a", "provider-a",
                content, null, calls.isEmpty() ? "stop" : "tool_calls", null, null, calls);
    }
}
