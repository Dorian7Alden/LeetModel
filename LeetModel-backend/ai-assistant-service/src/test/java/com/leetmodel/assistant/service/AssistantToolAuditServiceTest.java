package com.leetmodel.assistant.service;

import com.leetmodel.assistant.entity.AssistantToolCall;
import com.leetmodel.assistant.mapper.AssistantToolCallMapper;
import com.leetmodel.assistant.tool.AssistantToolExecutionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssistantToolAuditServiceTest {

    @Mock
    private AssistantToolCallMapper mapper;

    private AssistantToolAuditService service;

    @BeforeEach
    void setUp() {
        service = new AssistantToolAuditService(mapper);
        org.mockito.Mockito.lenient().doAnswer(invocation -> {
            AssistantToolCall call = invocation.getArgument(0);
            call.setId(99L);
            return 1;
        }).when(mapper).insert(any(AssistantToolCall.class));
    }

    @Test
    void persistsReceivedRunningAndCompletedTransitions() {
        when(mapper.markRunning(eq(99L), any(), any())).thenReturn(1);
        when(mapper.complete(eq(99L), any(), any(), anyLong(), any())).thenReturn(1);

        AssistantToolCall call = service.receive(context(), "provider-1", "search_problem",
                null, "planning-call-1");
        service.markRunning(call, "{\"code\":1003}");
        service.complete(call, "{\"items\":[]}", null, 12L);

        assertThat(call.getStatus()).isEqualTo("COMPLETED");
        assertThat(call.getArgumentsJson()).isEqualTo("{\"code\":1003}");
        assertThat(call.getResultSnapshotJson()).isEqualTo("{\"items\":[]}");
        assertThat(call.getPlanningAiCallId()).isEqualTo("planning-call-1");
        verify(mapper).complete(99L, "{\"items\":[]}", null, 12L,
                call.getFinishedAt());
    }

    @Test
    void rejectsBeforeExecutionWithoutPersistingRawArguments() {
        when(mapper.reject(eq(99L), eq("TOOL_ARGUMENT_INVALID"), any())).thenReturn(1);

        AssistantToolCall call = service.receive(context(), "provider-1", "search_problem",
                null, "planning-call-1");
        service.reject(call, "TOOL_ARGUMENT_INVALID");

        assertThat(call.getStatus()).isEqualTo("REJECTED");
        assertThat(call.getArgumentsJson()).isNull();
        assertThat(call.getErrorCode()).isEqualTo("TOOL_ARGUMENT_INVALID");
    }

    @Test
    void timeoutIsTerminal() {
        when(mapper.markRunning(eq(99L), any(), any())).thenReturn(1);
        when(mapper.fail(eq(99L), eq("TIMED_OUT"), eq("TOOL_TIMEOUT"), anyLong(), any()))
                .thenReturn(1);
        AssistantToolCall call = service.receive(context(), "provider-1", "search_problem",
                null, "planning-call-1");
        service.markRunning(call, "{\"code\":1003}");

        service.fail(call, "TIMED_OUT", "TOOL_TIMEOUT", 3000L);

        assertThat(call.getStatus()).isEqualTo("TIMED_OUT");
    }

    @Test
    void refusesInvalidFailureStatusAndLostConditionalUpdate() {
        AssistantToolCall call = new AssistantToolCall();
        call.setId(99L);

        assertThatThrownBy(() -> service.fail(call, "COMPLETED", "X", 1L))
                .isInstanceOf(IllegalArgumentException.class);
        when(mapper.reject(eq(99L), eq("TOOL_UNKNOWN"), any())).thenReturn(0);
        assertThatThrownBy(() -> service.reject(call, "TOOL_UNKNOWN"))
                .isInstanceOf(IllegalStateException.class);
    }

    private AssistantToolExecutionContext context() {
        return new AssistantToolExecutionContext(7L, 10L, 11L, 12L, 1, 1,
                "ASSISTANT_TOOLSET_0001", null, Instant.now().plusSeconds(3));
    }
}
