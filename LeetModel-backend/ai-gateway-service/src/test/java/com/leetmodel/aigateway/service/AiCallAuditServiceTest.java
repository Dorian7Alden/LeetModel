package com.leetmodel.aigateway.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.leetmodel.aigateway.entity.AiCallLog;
import com.leetmodel.aigateway.enums.AiGatewayErrorCode;
import com.leetmodel.aigateway.mapper.AiCallLogMapper;
import com.leetmodel.common.ai.model.AiChatRequest;
import com.leetmodel.common.ai.model.AiChatResponse;
import com.leetmodel.common.ai.model.AiProvider;
import com.leetmodel.common.ai.model.AiScene;
import com.leetmodel.common.ai.model.AiUsage;
import com.leetmodel.common.api.dto.AiCallQueryDTO;
import com.leetmodel.common.api.dto.AiCallStatsDTO;
import com.leetmodel.common.core.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
        AiChatResponse response = new AiChatResponse("provider-id", AiProvider.DEEPSEEK,
                "deepseek-test", "response-id", "private answer", "private reasoning",
                "stop", new AiUsage(10L, 0L, 10L, 5L, 1L, 15L, true));

        service.recordSuccess("call-1", request(), "DEEPSEEK", "configured-model", response, 321L);

        ArgumentCaptor<AiCallLog> captor = ArgumentCaptor.forClass(AiCallLog.class);
        verify(mapper).insert(captor.capture());
        AiCallLog saved = captor.getValue();
        assertThat(saved.getCallId()).isEqualTo("call-1");
        assertThat(saved.getScene()).isEqualTo("GENERAL_TEXT");
        assertThat(saved.getProvider()).isEqualTo("DEEPSEEK");
        assertThat(saved.getModel()).isEqualTo("deepseek-test");
        assertThat(saved.getStatus()).isEqualTo("SUCCEEDED");
        assertThat(saved.getTotalTokens()).isEqualTo(15L);
        assertThat(saved.getDurationMs()).isEqualTo(321L);
        assertThat(AiCallLog.class.getDeclaredFields())
                .extracting(java.lang.reflect.Field::getName)
                .doesNotContain("prompt", "content", "response", "apiKey");
    }

    @Test
    void shouldKeepBusinessFailureButSanitizeUnexpectedFailure() {
        service.recordFailure("call-business", request(), null, null,
                new BusinessException(AiGatewayErrorCode.PROVIDER_UNAVAILABLE), 9L);
        service.recordFailure("call-unexpected", request(), "DEEPSEEK", "model",
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

        service.recordSuccess("call-1", request(), "DEEPSEEK", "model",
                new AiChatResponse(null, AiProvider.DEEPSEEK, "model", null, "ok", null,
                        "stop", new AiUsage(null, null, null, null, null, null, false)), 1L);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldReturnRecentRowsAndEmptyStats() {
        AiCallLog row = new AiCallLog();
        row.setId(9007199254740993L);
        row.setCallId("call-1");
        row.setScene("GENERAL_TEXT");
        row.setProvider("DEEPSEEK");
        row.setModel("model");
        row.setStatus("SUCCEEDED");
        row.setCreateTime(LocalDateTime.now());
        when(mapper.selectList(any(Wrapper.class))).thenReturn(List.of(row));
        when(mapper.selectStats()).thenReturn(null);

        var rows = service.list(new AiCallQueryDTO(" general_text ", "deepseek", " model ",
                "succeeded", 10));
        AiCallStatsDTO stats = service.stats();

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getId()).isEqualTo(9007199254740993L);
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
        return new AiChatRequest(AiScene.GENERAL_TEXT, List.of(), 10, null, null, false);
    }
}
