package com.leetmodel.assistant.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leetmodel.assistant.entity.AssistantMessage;
import com.leetmodel.assistant.entity.AssistantToolCall;
import com.leetmodel.assistant.service.AssistantToolAuditService;
import com.leetmodel.assistant.workflow.AssistantProductionSnapshot;
import com.leetmodel.assistant.workflow.AssistantWorkflow;
import com.leetmodel.common.ai.model.AiChatResponse;
import com.leetmodel.common.ai.model.AiContentPart;
import com.leetmodel.common.ai.model.AiContentType;
import com.leetmodel.common.ai.model.AiMessage;
import com.leetmodel.common.ai.model.AiProvider;
import com.leetmodel.common.ai.model.AiRole;
import com.leetmodel.common.ai.model.AiToolCall;
import com.leetmodel.common.ai.model.AiToolDefinition;
import com.leetmodel.common.ai.model.AiToolType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssistantToolOrchestratorTest {

    @Mock
    private AssistantWorkflow workflow;
    @Mock
    private AssistantToolRegistry registry;
    @Mock
    private AssistantToolExecutionService executionService;
    @Mock
    private AssistantToolAuditService auditService;
    @Mock
    private AssistantTool<Object> tool;

    private AssistantToolOrchestrator orchestrator;
    private AssistantToolDescriptor descriptor;
    private AssistantMessage userMessage;
    private AssistantMessage assistantMessage;
    private AssistantProductionSnapshot snapshot;

    @BeforeEach
    void setUp() throws Exception {
        orchestrator = new AssistantToolOrchestrator(workflow, registry, executionService,
                auditService, new ObjectMapper());
        AiToolDefinition definition = new AiToolDefinition(AiToolType.FUNCTION,
                "search_problem", "查询已发布题目",
                Map.of("type", "object", "properties", Map.of(),
                        "additionalProperties", false));
        descriptor = new AssistantToolDescriptor("search_problem", "SEARCH_PROBLEM_0001",
                definition, false, Duration.ofSeconds(3),
                Set.of("ASSISTANT_TOOLS_NO_RAG_V1"));
        userMessage = message(11L, "USER", "1003 号题是什么");
        assistantMessage = message(12L, "ASSISTANT", null);
        snapshot = new AssistantProductionSnapshot("CFG", 1,
                "ASSISTANT_TOOLS_NO_RAG_V1", "PROMPT", "MODEL", "NONE", null);

        org.mockito.Mockito.lenient().when(tool.descriptor()).thenReturn(descriptor);
        org.mockito.Mockito.lenient().when(registry.definitions(anyString(), anyString()))
                .thenReturn(List.of(definition));
        org.mockito.Mockito.lenient().when(registry.find(anyString(), anyString()))
                .thenReturn(Optional.of(tool));
        org.mockito.Mockito.lenient().when(workflow.toolConversationMessages(any(), any(), any()))
                .thenReturn(List.of(textMessage(AiRole.USER, userMessage.getContent())));
        AtomicLong ids = new AtomicLong(100);
        org.mockito.Mockito.lenient().when(auditService.receive(any(), anyString(), anyString(),
                        any(), any()))
                .thenAnswer(invocation -> {
                    AssistantToolCall call = new AssistantToolCall();
                    call.setId(ids.incrementAndGet());
                    return call;
                });
    }

    @Test
    void ordinaryTextReturnsWithoutExecutingTool() throws Exception {
        when(workflow.toolChat(any(), any(), any(), any(), anyInt(), anyInt(), any(), any()))
                .thenReturn(response("范围内回答", List.of(), "final-call"));

        AssistantToolRunResult result = run();

        assertThat(result.response().content()).isEqualTo("范围内回答");
        assertThat(result.executedToolCalls()).isZero();
        assertThat(result.toolContextJson()).isNull();
        verify(executionService, never()).execute(any(), any());
        verify(auditService, never()).receive(any(), any(), any(), any(), any());
    }

    @Test
    void appendsAssistantToolAndUntrustedResultMessagesInProtocolOrder() throws Exception {
        AiToolCall modelCall = call("tool-call-1", "{\"code\":1003}");
        PreparedAssistantToolCall prepared = new PreparedAssistantToolCall(
                tool, new Object(), "{\"code\":1003}");
        when(registry.prepare(anyString(), anyString(), eq("search_problem"), anyString()))
                .thenReturn(prepared);
        when(executionService.execute(eq(prepared), any())).thenReturn(
                AssistantToolOutput.data("{\"items\":[{\"code\":1003}]}",
                        "{\"items\":[{\"code\":1003}]}"));
        List<List<AiMessage>> observedMessages = new ArrayList<>();
        when(workflow.toolChat(any(), any(), any(), any(), anyInt(), anyInt(), any(), any()))
                .thenAnswer(invocation -> {
                    observedMessages.add(List.copyOf(invocation.getArgument(0)));
                    return observedMessages.size() == 1
                            ? response(null, List.of(modelCall), "planning-call")
                            : response("题号 1003 是运输调度题。", List.of(), "final-call");
                });

        AssistantToolRunResult result = run();

        assertThat(result.executedToolCalls()).isEqualTo(1);
        assertThat(result.response().callId()).isEqualTo("final-call");
        assertThat(result.toolContextJson()).contains("search_problem", "1003");
        assertThat(observedMessages).hasSize(2);
        List<AiMessage> secondRequest = observedMessages.get(1);
        assertThat(secondRequest).extracting(AiMessage::role)
                .containsExactly(AiRole.USER, AiRole.ASSISTANT, AiRole.TOOL, AiRole.SYSTEM);
        assertThat(secondRequest.get(1).toolCalls()).containsExactly(modelCall);
        assertThat(secondRequest.get(2).toolCallId()).isEqualTo("tool-call-1");
        assertThat(secondRequest.get(2).content().get(0).text())
                .contains("BEGIN_UNTRUSTED_TOOL_RESULT", "1003", "END_UNTRUSTED_TOOL_RESULT");
        assertThat(secondRequest.get(3).content().get(0).text())
                .contains("不可信数据", "不能编造题目");
        verify(auditService).markRunning(any(), eq("{\"code\":1003}"));
        verify(auditService).complete(any(), contains("1003"), eq(null), anyLong());
    }

    @Test
    void rejectsEveryCallWhenOneModelResponseContainsMultipleTools() throws Exception {
        when(workflow.toolChat(any(), any(), any(), any(), anyInt(), anyInt(), any(), any()))
                .thenReturn(response(null, List.of(
                        call("call-1", "{\"code\":1003}"),
                        call("call-2", "{\"code\":1004}")), "planning-call"));

        assertThatThrownBy(this::run)
                .isInstanceOf(AssistantToolException.class)
                .extracting("errorCode").isEqualTo("TOOL_MULTIPLE_CALLS");
        verify(auditService, times(2)).receive(any(), anyString(), anyString(), any(), any());
        verify(auditService, times(2)).reject(any(), eq("TOOL_MULTIPLE_CALLS"));
        verify(executionService, never()).execute(any(), any());
    }

    @Test
    void rejectsRepeatedToolAndNormalizedArgumentsAsNoProgress() throws Exception {
        AiToolCall repeated = call("call-1", "{\"code\":1003}");
        PreparedAssistantToolCall prepared = new PreparedAssistantToolCall(
                tool, new Object(), "{\"code\":1003}");
        when(registry.prepare(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(prepared);
        when(executionService.execute(any(), any())).thenReturn(
                AssistantToolOutput.data("{\"items\":[]}", "{\"items\":[]}"));
        when(workflow.toolChat(any(), any(), any(), any(), anyInt(), anyInt(), any(), any()))
                .thenReturn(response(null, List.of(repeated), "planning-1"),
                        response(null, List.of(call("call-2", "{\"code\":1003}")), "planning-2"));

        assertThatThrownBy(this::run)
                .isInstanceOf(AssistantToolException.class)
                .extracting("errorCode").isEqualTo("TOOL_NO_PROGRESS");
        verify(executionService, times(1)).execute(any(), any());
        verify(auditService).reject(any(), eq("TOOL_NO_PROGRESS"));
    }

    @Test
    void rejectsThirdToolCallAfterTwoSuccessfulExecutions() throws Exception {
        when(registry.prepare(anyString(), anyString(), anyString(), anyString()))
                .thenAnswer(invocation -> new PreparedAssistantToolCall(tool, new Object(),
                        invocation.getArgument(3)));
        when(executionService.execute(any(), any())).thenReturn(
                AssistantToolOutput.data("{\"items\":[]}", "{\"items\":[]}"));
        when(workflow.toolChat(any(), any(), any(), any(), anyInt(), anyInt(), any(), any()))
                .thenReturn(response(null, List.of(call("call-1", "{\"code\":1001}")), "p1"),
                        response(null, List.of(call("call-2", "{\"code\":1002}")), "p2"),
                        response(null, List.of(call("call-3", "{\"code\":1003}")), "p3"));

        assertThatThrownBy(this::run)
                .isInstanceOf(AssistantToolException.class)
                .extracting("errorCode").isEqualTo("TOOL_CALL_LIMIT");
        verify(executionService, times(2)).execute(any(), any());
        verify(auditService).reject(any(), eq("TOOL_CALL_LIMIT"));
    }

    @Test
    void timeoutMovesRunningAuditToTimedOut() throws Exception {
        PreparedAssistantToolCall prepared = new PreparedAssistantToolCall(
                tool, new Object(), "{\"code\":1003}");
        when(registry.prepare(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(prepared);
        when(executionService.execute(any(), any()))
                .thenThrow(new AssistantToolException("TOOL_TIMEOUT", "超时"));
        when(workflow.toolChat(any(), any(), any(), any(), anyInt(), anyInt(), any(), any()))
                .thenReturn(response(null, List.of(call("call-1", "{\"code\":1003}")), "p1"));

        assertThatThrownBy(this::run)
                .isInstanceOf(AssistantToolException.class)
                .extracting("errorCode").isEqualTo("TOOL_TIMEOUT");
        verify(auditService).fail(any(), eq("TIMED_OUT"), eq("TOOL_TIMEOUT"), anyLong());
        verify(auditService, never()).complete(any(), any(), any(), anyLong());
    }

    @Test
    @SuppressWarnings("unchecked")
    void terminalKnowledgeToolReturnsNestedAnswerWithoutOuterRewrite() throws Exception {
        AssistantTool<Object> terminalTool = org.mockito.Mockito.mock(AssistantTool.class);
        AssistantToolDescriptor terminalDescriptor = new AssistantToolDescriptor(
                "explain_modeling_knowledge", "EXPLAIN_MODELING_KNOWLEDGE_0001",
                new AiToolDefinition(AiToolType.FUNCTION, "explain_modeling_knowledge",
                        "讲解数学建模知识", Map.of("type", "object", "properties", Map.of())),
                true, Duration.ofSeconds(120), Set.of("ASSISTANT_TOOLS_NO_RAG_V1"));
        when(terminalTool.descriptor()).thenReturn(terminalDescriptor);
        when(registry.find(anyString(), eq("explain_modeling_knowledge")))
                .thenReturn(Optional.of(terminalTool));
        AiToolCall knowledgeCall = new AiToolCall("knowledge-1",
                "explain_modeling_knowledge", "{\"topic\":\"层次分析法\"}");
        PreparedAssistantToolCall prepared = new PreparedAssistantToolCall(
                terminalTool, new Object(), "{\"topic\":\"层次分析法\"}");
        when(registry.prepare(anyString(), anyString(),
                eq("explain_modeling_knowledge"), anyString())).thenReturn(prepared);
        AiChatResponse nested = response("层次分析法用于多准则决策。", List.of(), "nested-call");
        when(executionService.execute(eq(prepared), any())).thenReturn(
                new AssistantToolOutput("{\"answerSha256\":\"abc\"}",
                        "{\"answerSha256\":\"abc\"}", nested));
        when(workflow.toolChat(any(), any(), any(), any(), anyInt(), anyInt(), any(), any()))
                .thenReturn(response(null, List.of(knowledgeCall), "planning-call"));

        AssistantToolRunResult result = run();

        assertThat(result.response().callId()).isEqualTo("nested-call");
        assertThat(result.response().content()).contains("多准则决策");
        assertThat(result.executedToolCalls()).isEqualTo(1);
        verify(workflow, times(1)).toolChat(any(), any(), any(), any(),
                anyInt(), anyInt(), any(), any());
        verify(auditService).complete(any(), contains("answerSha256"),
                eq("nested-call"), anyLong());
    }

    private AssistantToolRunResult run() throws Exception {
        return orchestrator.run(List.of(userMessage), userMessage, assistantMessage, snapshot,
                AssistantToolRegistry.TOOLSET_V1, 1, Instant.now().plusSeconds(240));
    }

    private AssistantMessage message(Long id, String role, String content) {
        AssistantMessage message = new AssistantMessage();
        message.setId(id);
        message.setUserId(7L);
        message.setConversationId(10L);
        message.setRole(role);
        message.setContent(content);
        return message;
    }

    private AiToolCall call(String id, String arguments) {
        return new AiToolCall(id, "search_problem", arguments);
    }

    private AiChatResponse response(String content, List<AiToolCall> calls, String callId) {
        return new AiChatResponse(callId, AiProvider.NEW_API, "model-a", "provider-1",
                content, null, calls.isEmpty() ? "stop" : "tool_calls", null, null, calls);
    }

    private AiMessage textMessage(AiRole role, String text) {
        return new AiMessage(role, List.of(new AiContentPart(AiContentType.TEXT, text, null)));
    }

}
