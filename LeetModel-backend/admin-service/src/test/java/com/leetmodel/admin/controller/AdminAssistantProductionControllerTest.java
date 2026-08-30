package com.leetmodel.admin.controller;

import com.leetmodel.admin.dto.AdminAssistantProductionChangeApplyDTO;
import com.leetmodel.admin.dto.AdminAssistantProductionChangePreviewDTO;
import com.leetmodel.admin.service.AdminFeignExecutor;
import com.leetmodel.common.api.dto.AssistantProductionChangeApplyDTO;
import com.leetmodel.common.api.dto.AssistantProductionChangePreviewRequestDTO;
import com.leetmodel.common.api.feign.AssistantFeignClient;
import com.leetmodel.common.core.result.Result;
import com.leetmodel.common.security.context.UserContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminAssistantProductionControllerTest {

    @Mock private AssistantFeignClient assistantClient;
    private AdminAssistantProductionController controller;

    @BeforeEach
    void setUp() {
        controller = new AdminAssistantProductionController(assistantClient,
                new AdminFeignExecutor());
    }

    @Test
    void previewMustInjectAuthenticatedAdministrator() {
        AdminAssistantProductionChangePreviewDTO request =
                new AdminAssistantProductionChangePreviewDTO();
        request.setAction("ACTIVATE");
        request.setExpectedRevision(5L);
        request.setTargetWorkflowVersion("ASSISTANT_RAG_V1");
        request.setRagIndexVersion("rag-v1-test");
        request.setReason("管理员验证固定索引工作流生产切换");
        when(assistantClient.previewProductionChange(any())).thenReturn(Result.ok());

        try (MockedStatic<UserContext> userContext = mockStatic(UserContext.class)) {
            userContext.when(UserContext::getUserId).thenReturn(91L);
            controller.preview(request);
        }

        ArgumentCaptor<AssistantProductionChangePreviewRequestDTO> captor =
                ArgumentCaptor.forClass(AssistantProductionChangePreviewRequestDTO.class);
        verify(assistantClient).previewProductionChange(captor.capture());
        assertThat(captor.getValue().getOperatorId()).isEqualTo(91L);
        assertThat(captor.getValue().getExpectedRevision()).isEqualTo(5L);
        assertThat(captor.getValue().getTargetWorkflowVersion())
                .isEqualTo("ASSISTANT_RAG_V1");
    }

    @Test
    void applyMustOnlyAddAuthenticatedAdministratorToFrozenRequest() {
        when(assistantClient.applyProductionChange(any())).thenReturn(Result.ok());

        try (MockedStatic<UserContext> userContext = mockStatic(UserContext.class)) {
            userContext.when(UserContext::getUserId).thenReturn(92L);
            controller.apply(new AdminAssistantProductionChangeApplyDTO(
                    "0123456789abcdef0123456789abcdef"));
        }

        ArgumentCaptor<AssistantProductionChangeApplyDTO> captor =
                ArgumentCaptor.forClass(AssistantProductionChangeApplyDTO.class);
        verify(assistantClient).applyProductionChange(captor.capture());
        assertThat(captor.getValue().getChangeRequestId())
                .isEqualTo("0123456789abcdef0123456789abcdef");
        assertThat(captor.getValue().getOperatorId()).isEqualTo(92L);
    }
}
