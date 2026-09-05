package com.leetmodel.assistant.controller;

import com.leetmodel.assistant.dto.ConversationRenameRequest;
import com.leetmodel.assistant.service.AssistantService;
import com.leetmodel.assistant.vo.ConversationVO;
import com.leetmodel.common.core.result.Result;
import com.leetmodel.common.security.context.UserContext;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AssistantControllerTest {

    private final AssistantService assistantService = mock(AssistantService.class);
    private final AssistantController controller = new AssistantController(assistantService);

    @Test
    void deleteConversationDelegatesToService() {
        try (MockedStatic<UserContext> userContext = mockStatic(UserContext.class)) {
            userContext.when(UserContext::getUserId).thenReturn(100L);

            Result<Void> result = controller.delete(1L);

            assertThat(result.isSuccess()).isTrue();
            verify(assistantService).deleteConversation(1L, 100L);
        }
    }

    @Test
    void renameConversationDelegatesToService() {
        try (MockedStatic<UserContext> userContext = mockStatic(UserContext.class)) {
            userContext.when(UserContext::getUserId).thenReturn(100L);
            ConversationVO vo = ConversationVO.builder().id(1L).title("新标题").build();
            when(assistantService.renameConversation(1L, 100L, "新标题")).thenReturn(vo);

            Result<ConversationVO> result = controller.rename(1L, new ConversationRenameRequest("新标题"));

            assertThat(result.getData().getTitle()).isEqualTo("新标题");
            verify(assistantService).renameConversation(1L, 100L, "新标题");
        }
    }

    @Test
    void getConversationWithCursorPagingDelegatesToService() {
        try (MockedStatic<UserContext> userContext = mockStatic(UserContext.class)) {
            userContext.when(UserContext::getUserId).thenReturn(100L);
            ConversationVO vo = ConversationVO.builder().id(1L).messages(List.of()).hasMore(false).build();
            when(assistantService.getConversation(1L, 100L, 50L, 20)).thenReturn(vo);

            Result<ConversationVO> result = controller.get(1L, 50L, 20);

            assertThat(result.getData()).isSameAs(vo);
            verify(assistantService).getConversation(1L, 100L, 50L, 20);
        }
    }
}
