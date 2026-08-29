package com.leetmodel.assistant.service;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.leetmodel.assistant.rag.workflow.RagWorkflowContext;
import com.leetmodel.assistant.rag.workflow.RagWorkflowContextProvider;
import com.leetmodel.assistant.workflow.AssistantWorkflow;
import com.leetmodel.common.ai.model.AiChatResponse;
import com.leetmodel.common.ai.model.AiProvider;
import com.leetmodel.common.ai.client.AiClientException;
import com.leetmodel.common.api.dto.AiExperimentRequestDTO;
import com.leetmodel.common.api.dto.AiExperimentSampleDTO;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AssistantExperimentServiceTest {
    private final AssistantWorkflow workflow = mock(AssistantWorkflow.class);
    private final RagWorkflowContextProvider rag = mock(RagWorkflowContextProvider.class);
    private final AssistantExperimentService service = new AssistantExperimentService(
            workflow, rag, JsonMapper.builder().findAndAddModules().build());

    @Test
    void noRagExperimentIsRepeatableAndReturnsCallIdWithoutRetrieval() {
        when(workflow.experimentReply(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(response("call-no-rag"));
        AiExperimentRequestDTO request = request("slot-1",
                AssistantExperimentService.NO_RAG_VERSION, null);

        var first = service.run(request);
        var second = service.run(request);

        assertThat(first.getStatus()).isEqualTo("SUCCEEDED");
        assertThat(first.getAiCallId()).isEqualTo("call-no-rag");
        assertThat(first.getRagIndexVersion()).isNull();
        assertThat(second.getExperimentRunId()).isEqualTo("slot-1");
        verifyNoInteractions(rag);
        verify(workflow, times(2)).experimentReply(eq("如何选择建模题目？"),
                argThat(context -> !context.present()), eq("slot-1"),
                eq(AssistantExperimentService.NO_RAG_VERSION),
                eq(AssistantExperimentService.MODEL_CONFIG), isNull(), isNull());
    }

    @Test
    void ragExperimentLocksRequestedPhysicalIndexVersion() {
        when(rag.retrieveExact("如何选择建模题目？", "rag-v1-fixed"))
                .thenReturn(new RagWorkflowContext("knowledge", "rag-v1-fixed", 2,
                        java.util.List.of("docs/submit.md")));
        when(workflow.experimentReply(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(response("call-rag"));

        var result = service.run(request("slot-rag", AssistantExperimentService.RAG_VERSION,
                "rag-v1-fixed"));

        assertThat(result.getStatus()).isEqualTo("SUCCEEDED");
        assertThat(result.getAiCallId()).isEqualTo("call-rag");
        assertThat(result.getRagIndexVersion()).isEqualTo("rag-v1-fixed");
        assertThat(result.getOutputJson()).contains("\"retrievedChunkCount\":2", "docs/submit.md");
        verify(rag).retrieveExact("如何选择建模题目？", "rag-v1-fixed");
    }

    @Test
    void unknownGatewayResultIsNotCollapsedIntoRetryableDependencyFailure() {
        when(workflow.experimentReply(any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new AiClientException(51213, "unknown"));

        var result = service.run(request("slot-unknown",
                AssistantExperimentService.NO_RAG_VERSION, null));

        assertThat(result.getStatus()).isEqualTo("UNKNOWN");
        assertThat(result.getFailureType()).isEqualTo("UNKNOWN");
        assertThat(result.getErrorMessage()).contains("禁止自动重试");
    }

    private AiExperimentRequestDTO request(String runId, String workflowVersion, String ragVersion) {
        return new AiExperimentRequestDTO(runId, "ASSISTANT",
                new AiExperimentSampleDTO("QUESTION", "ASSISTANT_QUESTION_V1",
                        "{\"question\":\"如何选择建模题目？\"}"), workflowVersion,
                AssistantExperimentService.MODEL_CONFIG, ragVersion, "P3");
    }

    private AiChatResponse response(String callId) {
        return new AiChatResponse(callId, AiProvider.NEW_API, "deepseek-v4-flash",
                "upstream", "answer", null, "stop", null);
    }
}
