package com.leetmodel.assistant.tool;

import com.leetmodel.common.ai.model.AiToolDefinition;
import com.leetmodel.common.ai.model.AiToolType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AssistantToolExecutionServiceTest {

    private ExecutorService executor;
    private AssistantToolExecutionService service;

    @BeforeEach
    void setUp() {
        executor = Executors.newSingleThreadExecutor();
        service = new AssistantToolExecutionService(executor);
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    void interruptsToolWhenDescriptorTimeoutExpires() {
        AssistantTool<Object> tool = tool(Duration.ofMillis(20));
        when(tool.execute(any(), any())).thenAnswer(invocation -> {
            Thread.sleep(500);
            return AssistantToolOutput.data("{}", "{}");
        });
        PreparedAssistantToolCall prepared = new PreparedAssistantToolCall(tool, new Object(), "{}");

        assertThatThrownBy(() -> service.execute(prepared,
                context(Instant.now().plusSeconds(3))))
                .isInstanceOf(AssistantToolException.class)
                .extracting("errorCode").isEqualTo("TOOL_TIMEOUT");
    }

    @Test
    void expiredReplyDeadlinePreventsToolDispatch() {
        AssistantTool<Object> tool = tool(Duration.ofSeconds(3));
        PreparedAssistantToolCall prepared = new PreparedAssistantToolCall(tool, new Object(), "{}");

        assertThatThrownBy(() -> service.execute(prepared,
                context(Instant.now().minusMillis(1))))
                .isInstanceOf(AssistantToolException.class)
                .extracting("errorCode").isEqualTo("TOOL_TIMEOUT");
        verify(tool, never()).execute(any(), any());
    }

    @SuppressWarnings("unchecked")
    private AssistantTool<Object> tool(Duration timeout) {
        AssistantTool<Object> tool = mock(AssistantTool.class);
        AiToolDefinition definition = new AiToolDefinition(AiToolType.FUNCTION,
                "search_problem", "查询题目",
                Map.of("type", "object", "properties", Map.of()));
        when(tool.descriptor()).thenReturn(new AssistantToolDescriptor(
                "search_problem", "SEARCH_PROBLEM_0001", definition,
                false, timeout, Set.of("ASSISTANT_TOOLS_NO_RAG_V1")));
        return tool;
    }

    private AssistantToolExecutionContext context(Instant deadline) {
        return new AssistantToolExecutionContext(7L, 10L, 11L, 12L, 1, 1,
                AssistantToolRegistry.TOOLSET_V1, null, deadline);
    }
}
