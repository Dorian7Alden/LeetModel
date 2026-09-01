package com.leetmodel.aigateway.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.leetmodel.aigateway.entity.AiCallLog;
import com.leetmodel.aigateway.enums.AiGatewayErrorCode;
import com.leetmodel.aigateway.mapper.AiCallLogMapper;
import com.leetmodel.common.ai.model.AiChatRequest;
import com.leetmodel.common.ai.model.AiChatResponse;
import com.leetmodel.common.ai.model.AiCallContext;
import com.leetmodel.common.ai.model.AiCallPriority;
import com.leetmodel.common.ai.model.AiFeatureCode;
import com.leetmodel.common.ai.model.AiModality;
import com.leetmodel.common.ai.model.AiOperationCode;
import com.leetmodel.common.ai.model.AiProvider;
import com.leetmodel.common.ai.model.AiUsage;
import com.leetmodel.common.ai.model.AiMetricCompleteness;
import com.leetmodel.common.api.dto.AiCallQueryDTO;
import com.leetmodel.common.api.dto.AiCallStatsDTO;
import com.leetmodel.common.core.exception.BusinessException;
import com.leetmodel.common.core.util.TraceIdUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiCallAuditServiceTest {

    @Mock
    private AiCallLogMapper mapper;
    private AiCallAuditService service;

    @BeforeEach
    void setUp() {
        service = new AiCallAuditService(mapper);
    }

    @Test
    void shouldPersistOnlySuccessMetadata() {
        AiChatResponse response = new AiChatResponse("provider-id", AiProvider.NEW_API,
                "deepseek-test", "response-id", "private answer", "private reasoning",
                "stop", new AiUsage(10L, 5L, 1L, 0L, null, 10L, 15L,
                AiMetricCompleteness.COMPLETE));

        service.recordSuccess("call-1", request(), "NEW_API", "configured-model", response, 321L);

        ArgumentCaptor<AiCallLog> captor = ArgumentCaptor.forClass(AiCallLog.class);
        verify(mapper).insert(captor.capture());
        AiCallLog saved = captor.getValue();
        assertThat(saved.getCallId()).isEqualTo("call-1");
        assertThat(saved.getScene()).isEqualTo("TEXT");
        assertThat(saved.getModality()).isEqualTo("TEXT");
        assertThat(saved.getCallerService()).isEqualTo("ai-assistant-service");
        assertThat(saved.getFeatureCode()).isEqualTo("AI_ASSISTANT");
        assertThat(saved.getOperationCode()).isEqualTo("CHAT_REPLY");
        assertThat(saved.getBusinessTaskId()).isEqualTo("message:1");
        assertThat(saved.getRagIndexVersion()).isEqualTo("rag-v1-test");
        assertThat(saved.getProviderResponseId()).isEqualTo("response-id");
        assertThat(saved.getNewApiRequestId()).isNull();
        assertThat(saved.getProvider()).isEqualTo("NEW_API");
        assertThat(saved.getModel()).isEqualTo("deepseek-test");
        assertThat(saved.getStatus()).isEqualTo("SUCCEEDED");
        assertThat(saved.getTotalTokens()).isEqualTo(15L);
        assertThat(saved.getUsageCompleteness()).isEqualTo("COMPLETE");
        assertThat(saved.getCostCompleteness()).isEqualTo("UNKNOWN");
        assertThat(saved.getQueueMs()).isZero();
        assertThat(saved.getExecutionMs()).isEqualTo(321L);
        assertThat(saved.getTotalMs()).isEqualTo(321L);
        assertThat(saved.getDurationMs()).isEqualTo(321L);
        assertThat(AiCallLog.class.getDeclaredFields())
                .extracting(java.lang.reflect.Field::getName)
                .doesNotContain("prompt", "content", "response", "apiKey");
    }

    @Test
    void shouldPersistTraceIdForAssociationQuery() {
        TraceIdUtil.setTraceId("trace-mq-ai-1");
        try {
            service.recordFailure("call-trace", request(), "NEW_API", "model",
                    new IllegalStateException("failed"), 10L);
        } finally {
            TraceIdUtil.removeTraceId();
        }

        ArgumentCaptor<AiCallLog> captor = ArgumentCaptor.forClass(AiCallLog.class);
        verify(mapper).insert(captor.capture());
        assertThat(captor.getValue().getTraceId()).isEqualTo("trace-mq-ai-1");
    }

    @Test
    void shouldKeepBusinessFailureButSanitizeUnexpectedFailure() {
        service.recordFailure("call-business", request(), null, null,
                new BusinessException(AiGatewayErrorCode.PROVIDER_UNAVAILABLE), 9L);
        service.recordFailure("call-unexpected", request(), "NEW_API", "model",
                new IllegalStateException("jdbc:mysql://root:secret@localhost/private"), 10L);

        ArgumentCaptor<AiCallLog> captor = ArgumentCaptor.forClass(AiCallLog.class);
        verify(mapper, org.mockito.Mockito.times(2)).insert(captor.capture());
        assertThat(captor.getAllValues().get(0).getErrorCode()).isEqualTo(51202);
        assertThat(captor.getAllValues().get(0).getErrorMessage()).isEqualTo("AI 供应商暂不可用");
        assertThat(captor.getAllValues().get(1).getErrorCode()).isEqualTo(50001);
        assertThat(captor.getAllValues().get(1).getErrorMessage()).isEqualTo("AI 服务调用失败");
    }

    @Test
    void auditFailureMustNotChangeBusinessResult() {
        doThrow(new IllegalStateException("db down")).when(mapper).insert(any(AiCallLog.class));

        service.recordSuccess("call-1", request(), "NEW_API", "model",
                new AiChatResponse(null, AiProvider.NEW_API, "model", null, "ok", null,
                        "stop", new AiUsage(null, null, null, null, null, null, null,
                        AiMetricCompleteness.UNKNOWN)), 1L);
    }

    @Test
    void shouldPersistPartialAndUnknownUsageWithoutZeroFilling() {
        service.recordSuccess("call-partial", request(), "NEW_API", "model",
                new AiChatResponse(null, AiProvider.NEW_API, "model", null, "ok", null,
                        "stop", new AiUsage(8L, null, null, 2L, null, null, null,
                        AiMetricCompleteness.PARTIAL)), 2L);
        service.recordSuccess("call-unknown", request(), "NEW_API", "model",
                new AiChatResponse(null, AiProvider.NEW_API, "model", null, "ok", null,
                        "stop", null), 3L);

        ArgumentCaptor<AiCallLog> captor = ArgumentCaptor.forClass(AiCallLog.class);
        verify(mapper, org.mockito.Mockito.times(2)).insert(captor.capture());
        AiCallLog partial = captor.getAllValues().get(0);
        AiCallLog unknown = captor.getAllValues().get(1);
        assertThat(partial.getUsageCompleteness()).isEqualTo("PARTIAL");
        assertThat(partial.getInputTokens()).isEqualTo(8L);
        assertThat(partial.getCacheHitTokens()).isEqualTo(2L);
        assertThat(partial.getOutputTokens()).isNull();
        assertThat(partial.getTotalTokens()).isNull();
        assertThat(unknown.getUsageCompleteness()).isEqualTo("UNKNOWN");
        assertThat(unknown.getInputTokens()).isNull();
        assertThat(unknown.getTotalTokens()).isNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldReturnRecentRowsAndEmptyStats() {
        AiCallLog row = new AiCallLog();
        row.setId(9007199254740993L);
        row.setCallId("call-1");
        row.setScene("TEXT");
        row.setModality("TEXT");
        row.setFeatureCode("PAPER_REVIEW");
        row.setOperationCode("EXPERIMENT_REVIEW");
        row.setEvaluationTaskId("evaluation:7");
        row.setProvider("NEW_API");
        row.setModel("model");
        row.setStatus("SUCCEEDED");
        row.setCreateTime(LocalDateTime.now());
        when(mapper.selectList(any(Wrapper.class))).thenReturn(List.of(row));
        when(mapper.selectStats(any(AiCallQueryDTO.class))).thenReturn(null);

        var rows = service.list(new AiCallQueryDTO(" general_text ", "new_api", " model ",
                "succeeded", 10));
        AiCallStatsDTO stats = service.stats();

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getId()).isEqualTo(9007199254740993L);
        assertThat(rows.get(0).getEvaluationTaskId()).isEqualTo("evaluation:7");
        assertThat(stats).usingRecursiveComparison()
                .isEqualTo(new AiCallStatsDTO(0L, 0L, 0L, 0L, 0L));
    }

    @Test
    @SuppressWarnings("unchecked")
    void omittedFiltersMustNotCauseNullPointer() {
        when(mapper.selectList(any(Wrapper.class))).thenReturn(List.of());

        assertThat(service.list(new AiCallQueryDTO(null, null, null, null, 20))).isEmpty();
    }

    private AiChatRequest request() {
        AiCallContext context = new AiCallContext("ai-assistant-service",
                AiFeatureCode.AI_ASSISTANT, AiOperationCode.CHAT_REPLY, "message:1",
                "ASSISTANT_CHAT_V1", "PROMPT_ASSISTANT_CHAT_0001",
                "MODEL_CFG_ASSISTANT_TEXT_0001", null, "rag-v1-test", AiCallPriority.P0,
                "assistant:message:1", Instant.parse("2099-01-01T00:00:00Z"));
        return new AiChatRequest(AiModality.TEXT, context, List.of(), 10, null, null, false);
    }
}
