package com.leetmodel.aigateway.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.leetmodel.aigateway.entity.AiCallTask;
import com.leetmodel.aigateway.config.AiSchedulingProperties;
import com.leetmodel.aigateway.mapper.AiCallTaskMapper;
import com.leetmodel.aigateway.model.ModelExecutionSnapshot;
import com.leetmodel.aigateway.scheduling.AiPriorityPolicy;
import com.leetmodel.aigateway.scheduling.AiQueueAdmissionService;
import com.leetmodel.aigateway.scheduling.AiTaskWaitRegistry;
import com.leetmodel.common.ai.model.AiCallContext;
import com.leetmodel.common.ai.model.AiCallPriority;
import com.leetmodel.common.ai.model.AiChatRequest;
import com.leetmodel.common.ai.model.AiChatResponse;
import com.leetmodel.common.ai.model.AiContentPart;
import com.leetmodel.common.ai.model.AiContentType;
import com.leetmodel.common.ai.model.AiEmbeddingRequest;
import com.leetmodel.common.ai.model.AiEmbeddingResponse;
import com.leetmodel.common.ai.model.AiEmbeddingVector;
import com.leetmodel.common.ai.model.AiFeatureCode;
import com.leetmodel.common.ai.model.AiMessage;
import com.leetmodel.common.ai.model.AiModality;
import com.leetmodel.common.ai.model.AiOperationCode;
import com.leetmodel.common.ai.model.AiProvider;
import com.leetmodel.common.ai.model.AiRole;
import com.leetmodel.common.core.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataAccessResourceFailureException;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AiScheduledCallServiceTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();
    private final AiQueueAdmissionService admission = mock(AiQueueAdmissionService.class);
    private final AiCallTaskMapper mapper = mock(AiCallTaskMapper.class);
    private final ModelExecutionConfigService executionConfigs = executionConfigs();
    private final AiScheduledCallService service = new AiScheduledCallService(objectMapper,
            new AiPriorityPolicy(), admission, mapper, new AiTaskWaitRegistry(), executionConfigs);

    @Test
    void schedulesRetrievalAsP0AndPreservesQueuedCallId() throws Exception {
        AiChatResponse response = new AiChatResponse("queued-call", AiProvider.NEW_API,
                "deepseek-test", "provider-call", "answer", null, "stop", null);
        completeAdmissionWith(response);
        AiChatRequest request = new AiChatRequest(AiModality.TEXT,
                context(AiOperationCode.RETRIEVE_CONTEXT, AiCallPriority.P4, "retrieve-1"),
                List.of(new AiMessage(AiRole.USER,
                        List.of(new AiContentPart(AiContentType.TEXT, "question", null)))),
                64, null, null, false);

        AiChatResponse actual = service.chat(request).join();

        assertThat(actual).isEqualTo(response);
        ArgumentCaptor<AiCallTask> task = ArgumentCaptor.forClass(AiCallTask.class);
        verify(admission).enqueue(task.capture());
        assertThat(task.getValue().getEffectivePriority()).isEqualTo("P0");
        assertThat(task.getValue().getCallType()).isEqualTo("CHAT");
        assertThat(task.getValue().getRequestPayload()).contains("question");
    }

    @Test
    void schedulesIndexEmbeddingAsP4() throws Exception {
        AiEmbeddingResponse response = new AiEmbeddingResponse("queued-embedding", "rag-embedding",
                "qwen3.7-text-embedding", 2,
                List.of(new AiEmbeddingVector(0, List.of(0.1F, 0.2F))), null, null);
        completeAdmissionWith(response);
        AiEmbeddingRequest request = AiEmbeddingRequest.single("rag-embedding",
                context(AiOperationCode.INDEX_DOCUMENTS, AiCallPriority.P0, "index-1"), "document");

        assertThat(service.embed(request).join()).isEqualTo(response);
        ArgumentCaptor<AiCallTask> task = ArgumentCaptor.forClass(AiCallTask.class);
        verify(admission).enqueue(task.capture());
        assertThat(task.getValue().getEffectivePriority()).isEqualTo("P4");
        assertThat(task.getValue().getCallType()).isEqualTo("EMBEDDING");
    }

    @Test
    void resultTimeoutDoesNotCreateDuplicateUpstreamTask() {
        AiCallTaskMapper taskMapper = mock(AiCallTaskMapper.class);
        AtomicReference<AiCallTask> stored = new AtomicReference<>();
        when(taskMapper.selectByIdempotency("ai-assistant-service", "timeout-1"))
                .thenAnswer(ignored -> stored.get());
        when(taskMapper.countActive()).thenReturn(0L);
        when(taskMapper.countActiveNonP0()).thenReturn(0L);
        doAnswer(invocation -> {
            stored.set(invocation.getArgument(0));
            return 1;
        }).when(taskMapper).insert(any(AiCallTask.class));
        when(taskMapper.selectByTaskId(any())).thenAnswer(ignored -> stored.get());
        AiScheduledCallService scheduled = new AiScheduledCallService(objectMapper,
                new AiPriorityPolicy(), new AiQueueAdmissionService(taskMapper, new AiSchedulingProperties()),
                taskMapper, new AiTaskWaitRegistry(), executionConfigs);
        AiCallContext context = new AiCallContext("ai-assistant-service", AiFeatureCode.RAG,
                AiOperationCode.RETRIEVE_CONTEXT, "task", null, null, "MODEL_CFG_RAG_V1",
                null, "rag-v1", AiCallPriority.P0, "timeout-1", Instant.now().plusMillis(80));
        AiChatRequest request = new AiChatRequest(AiModality.TEXT, context,
                List.of(new AiMessage(AiRole.USER,
                        List.of(new AiContentPart(AiContentType.TEXT, "question", null)))),
                64, null, null, false);

        assertThatThrownBy(() -> scheduled.chat(request).join())
                .isInstanceOf(CompletionException.class);
        assertThatThrownBy(() -> scheduled.chat(request).join())
                .isInstanceOf(CompletionException.class);
        verify(taskMapper, times(1)).insert(any(AiCallTask.class));
    }

    @Test
    void propagatesUnknownUpstreamResultAsNonRetryableCode() {
        when(admission.enqueue(any())).thenAnswer(invocation -> {
            AiCallTask task = invocation.getArgument(0);
            task.setState("FAILED");
            task.setErrorCode("AI_UPSTREAM_RESULT_UNKNOWN");
            return new AiQueueAdmissionService.AdmissionResult(task, true, null);
        });
        AiChatRequest request = new AiChatRequest(AiModality.TEXT,
                context(AiOperationCode.RETRIEVE_CONTEXT, AiCallPriority.P3, "unknown-1"),
                List.of(new AiMessage(AiRole.USER,
                        List.of(new AiContentPart(AiContentType.TEXT, "question", null)))),
                64, null, null, false);

        assertThatThrownBy(() -> service.chat(request).join())
                .isInstanceOf(CompletionException.class)
                .cause().isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(51213);
    }

    @Test
    void databaseFailureRejectsBeforeRegisteringOrDispatchingWork() {
        AiQueueAdmissionService failedAdmission = mock(AiQueueAdmissionService.class);
        AiCallTaskMapper taskMapper = mock(AiCallTaskMapper.class);
        AiTaskWaitRegistry waitRegistry = mock(AiTaskWaitRegistry.class);
        when(failedAdmission.enqueue(any())).thenThrow(new DataAccessResourceFailureException("db down"));
        AiScheduledCallService scheduled = new AiScheduledCallService(objectMapper,
                new AiPriorityPolicy(), failedAdmission, taskMapper, waitRegistry, executionConfigs);
        AiChatRequest request = new AiChatRequest(AiModality.TEXT,
                context(AiOperationCode.RETRIEVE_CONTEXT, AiCallPriority.P0, "db-failure"),
                List.of(new AiMessage(AiRole.USER,
                        List.of(new AiContentPart(AiContentType.TEXT, "question", null)))),
                64, null, null, false);

        assertThatThrownBy(() -> scheduled.chat(request))
                .isInstanceOf(DataAccessResourceFailureException.class);
        verifyNoInteractions(taskMapper, waitRegistry);
    }

    private void completeAdmissionWith(Object response) throws Exception {
        when(admission.enqueue(any())).thenAnswer(invocation -> {
            AiCallTask task = invocation.getArgument(0);
            task.setState("SUCCEEDED");
            task.setResultPayload(objectMapper.writeValueAsString(response));
            return new AiQueueAdmissionService.AdmissionResult(task, true, null);
        });
    }

    private AiCallContext context(AiOperationCode operation, AiCallPriority declared, String idempotencyKey) {
        return new AiCallContext("ai-assistant-service", AiFeatureCode.RAG, operation,
                "task", null, null, "MODEL_CFG_RAG_V1", null, "rag-v1",
                declared, idempotencyKey, Instant.now().plusSeconds(120));
    }

    private ModelExecutionConfigService executionConfigs() {
        ModelExecutionConfigService service = mock(ModelExecutionConfigService.class);
        when(service.resolve(any(), any(), any())).thenAnswer(invocation -> {
            String type = invocation.getArgument(0);
            Object request = invocation.getArgument(2);
            String logical = request instanceof AiEmbeddingRequest embedding ? embedding.logicalModel() : null;
            return new ModelExecutionSnapshot("MODEL_CFG_RAG_V1", type, logical,
                    AiProvider.NEW_API, "locked-model", AiModality.TEXT,
                    64, null, null, false, 2, 32, 8192, 65536,
                    null, null);
        });
        return service;
    }
}
