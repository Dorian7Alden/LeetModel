package com.leetmodel.aigateway.controller;

import com.leetmodel.aigateway.service.AiCallAuditService;
import com.leetmodel.aigateway.service.AiChatService;
import com.leetmodel.aigateway.service.AiModelService;
import com.leetmodel.common.ai.model.AiChatRequest;
import com.leetmodel.common.ai.model.AiChatResponse;
import com.leetmodel.common.ai.model.AiContentPart;
import com.leetmodel.common.ai.model.AiContentType;
import com.leetmodel.common.ai.model.AiMessage;
import com.leetmodel.common.ai.model.AiProvider;
import com.leetmodel.common.ai.model.AiRole;
import com.leetmodel.common.ai.model.AiScene;
import com.leetmodel.common.ai.model.AiUsage;
import com.leetmodel.common.ai.model.AiMetricCompleteness;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InternalAiControllerTest {

    @Test
    void shouldReturnUnifiedChatResponse() {
        AiChatService chatService = mock(AiChatService.class);
        InternalAiController controller = new InternalAiController(chatService,
                mock(AiModelService.class), mock(AiCallAuditService.class));
        AiChatRequest request = new AiChatRequest(AiScene.GENERAL_TEXT,
                List.of(new AiMessage(AiRole.USER,
                        List.of(new AiContentPart(AiContentType.TEXT, "hello", null)))),
                64, null, null, false);
        AiChatResponse response = new AiChatResponse("call-1", AiProvider.NEW_API,
                "deepseek-v4-flash", "relay-1", "ok", null, "stop",
                new AiUsage(1L, 1L, null, null, null, 1L, 2L,
                        AiMetricCompleteness.COMPLETE));
        when(chatService.chat(request)).thenReturn(response);

        var result = controller.chat(request);

        assertThat(result.getData()).isSameAs(response);
        verify(chatService).chat(request);
    }
}
