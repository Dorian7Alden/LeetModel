package com.leetmodel.assistant.workflow;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leetmodel.assistant.entity.AssistantMessage;
import com.leetmodel.assistant.rag.workflow.RagWorkflowContext;
import com.leetmodel.assistant.rag.workflow.RagWorkflowContextProvider;
import com.leetmodel.common.ai.client.AiClient;
import com.leetmodel.common.ai.model.AiCallContext;
import com.leetmodel.common.ai.model.AiCallPriority;
import com.leetmodel.common.ai.model.AiChatRequest;
import com.leetmodel.common.ai.model.AiChatResponse;
import com.leetmodel.common.ai.model.AiContentPart;
import com.leetmodel.common.ai.model.AiContentType;
import com.leetmodel.common.ai.model.AiMessage;
import com.leetmodel.common.ai.model.AiFeatureCode;
import com.leetmodel.common.ai.model.AiModality;
import com.leetmodel.common.ai.model.AiOperationCode;
import com.leetmodel.common.ai.model.AiResponseFormat;
import com.leetmodel.common.ai.model.AiRole;
import com.leetmodel.common.ai.model.AiToolChoice;
import com.leetmodel.common.ai.model.AiToolChoiceType;
import com.leetmodel.common.ai.model.AiToolDefinition;
import com.leetmodel.common.api.dto.ProblemOptionDTO;
import org.springframework.core.io.ClassPathResource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * AI 客服首版文本对话与受控题目候选注入工作流。
 */
@Component
public class AssistantWorkflow {

    private static final List<String> PROBLEM_KEYWORDS = List.of(
            "选题", "推荐题", "题目推荐", "适合的题", "练习题", "problem", "recommend");

    private final AiClient aiClient;
    private final ObjectMapper objectMapper;
    private final RagWorkflowContextProvider ragContextProvider;
    private final String systemPrompt;

    public AssistantWorkflow(AiClient aiClient, ObjectMapper objectMapper) throws Exception {
        this(aiClient, objectMapper, RagWorkflowContextProvider.disabled());
    }

    @Autowired
    public AssistantWorkflow(AiClient aiClient, ObjectMapper objectMapper,
                             RagWorkflowContextProvider ragContextProvider) throws Exception {
        this.aiClient = aiClient;
        this.objectMapper = objectMapper;
        this.ragContextProvider = ragContextProvider;
        this.systemPrompt = new ClassPathResource("prompts/assistant-v1.st")
                .getContentAsString(StandardCharsets.UTF_8);
    }

    /**
     * 判断当前问题是否需要只读题目候选工具。
     *
     * @param content 用户消息
     * @return 是否查询题目
     */
    public boolean needsProblemTool(String content) {
        String normalized = content == null ? "" : content.toLowerCase(Locale.ROOT);
        return PROBLEM_KEYWORDS.stream().anyMatch(normalized::contains);
    }

    /**
     * 生成一次客服回复。
     *
     * @param history 最近已完成消息
     * @param currentUserMessage 当前用户消息
     * @param candidates 受控题目候选，非选题问题时为 null；空列表表示题库没有候选
     * @return AI 网关响应
     */
    public AiChatResponse reply(List<AssistantMessage> history, AssistantMessage currentUserMessage,
                                List<ProblemOptionDTO> candidates,
                                AssistantProductionSnapshot snapshot) throws JsonProcessingException {
        List<AiMessage> messages = productionMessages(history, currentUserMessage,
                candidates, snapshot);
        String taskId = currentUserMessage.getId() == null
                ? "transient:" + UUID.randomUUID() : "message:" + currentUserMessage.getId();
        AiCallContext context = new AiCallContext(
                "ai-assistant-service", AiFeatureCode.AI_ASSISTANT, AiOperationCode.CHAT_REPLY,
                taskId, snapshot.workflowVersion(), snapshot.promptVersion(),
                snapshot.modelExecutionConfigVersion(), null, snapshot.ragIndexVersion(), AiCallPriority.P0,
                "assistant:" + taskId, Instant.now().plusSeconds(240));
        AiChatResponse response = aiClient.chat(new AiChatRequest(
                AiModality.TEXT, context, messages, 1500, 0.2, AiResponseFormat.TEXT, false));
        if (response == null || response.content() == null || response.content().isBlank()) {
            throw new IllegalArgumentException("AI 网关未返回客服回复");
        }
        return response;
    }

    /**
     * 构造标准工具调用使用的生产对话消息，不注入旧关键词预取候选。
     *
     * @param history 最近已完成消息
     * @param currentUserMessage 当前用户消息
     * @param snapshot 不可变生产快照
     * @return 系统、RAG 和历史消息
     */
    public List<AiMessage> toolConversationMessages(List<AssistantMessage> history,
                                                    AssistantMessage currentUserMessage,
                                                    AssistantProductionSnapshot snapshot)
            throws JsonProcessingException {
        return productionMessages(history, currentUserMessage, null, snapshot);
    }

    /**
     * 执行工具循环中的一次规划或最终回答调用。
     *
     * @param messages 当前完整对话消息
     * @param tools 当前快照允许的工具定义
     * @param currentUserMessage 触发消息
     * @param assistantMessageId 承载最终回复的消息
     * @param attemptNo 生成尝试序号
     * @param chatSequence 本次尝试内的 AI 调用序号
     * @param deadline 整条回复共享截止时间
     * @param snapshot 不可变生产快照
     * @return 文本或结构化工具调用响应
     */
    public AiChatResponse toolChat(List<AiMessage> messages, List<AiToolDefinition> tools,
                                   AssistantMessage currentUserMessage, Long assistantMessageId,
                                   int attemptNo, int chatSequence, Instant deadline,
                                   AssistantProductionSnapshot snapshot) {
        String taskId = "message:" + currentUserMessage.getId();
        AiCallContext context = new AiCallContext(
                "ai-assistant-service", AiFeatureCode.AI_ASSISTANT, AiOperationCode.CHAT_REPLY,
                taskId, snapshot.workflowVersion(), snapshot.promptVersion(),
                snapshot.modelExecutionConfigVersion(), null, snapshot.ragIndexVersion(),
                AiCallPriority.P0,
                "assistant:" + assistantMessageId + ":attempt:" + attemptNo
                        + ":chat:" + chatSequence,
                deadline);
        AiChatResponse response = aiClient.chat(new AiChatRequest(
                AiModality.TEXT, context, List.copyOf(messages), 1500, 0.2,
                AiResponseFormat.TEXT, false, tools,
                new AiToolChoice(AiToolChoiceType.AUTO, null)));
        boolean hasToolCalls = response != null && response.toolCalls() != null
                && !response.toolCalls().isEmpty();
        boolean hasContent = response != null && response.content() != null
                && !response.content().isBlank();
        if (!hasToolCalls && !hasContent) {
            throw new IllegalArgumentException("AI 网关未返回客服回复或工具调用");
        }
        return response;
    }

    /** 构造正式回复共享的系统、RAG 和历史消息。 */
    private List<AiMessage> productionMessages(List<AssistantMessage> history,
                                               AssistantMessage currentUserMessage,
                                               List<ProblemOptionDTO> candidates,
                                               AssistantProductionSnapshot snapshot)
            throws JsonProcessingException {
        RagWorkflowContext ragContext = productionRagContext(currentUserMessage.getContent(), snapshot);
        List<AiMessage> messages = new ArrayList<>();
        messages.add(message(AiRole.SYSTEM, systemPrompt));
        if (ragContext.present()) {
            messages.add(message(AiRole.SYSTEM, ragContext.text()));
        }
        for (AssistantMessage item : history) {
            AiRole role = "ASSISTANT".equals(item.getRole()) ? AiRole.ASSISTANT : AiRole.USER;
            String content = item.getContent();
            if (Objects.equals(item.getId(), currentUserMessage.getId()) && candidates != null) {
                content += "\n\n系统只读题目候选（只能依据这些数据推荐）：\n"
                        + objectMapper.writeValueAsString(candidates);
            }
            messages.add(message(role, content));
        }
        return messages;
    }

    private RagWorkflowContext productionRagContext(String question,
                                                     AssistantProductionSnapshot snapshot) {
        if ("NONE".equals(snapshot.ragMode()) && snapshot.ragIndexVersion() == null) {
            return RagWorkflowContext.empty();
        }
        if ("FIXED_INDEX".equals(snapshot.ragMode())
                && snapshot.ragIndexVersion() != null) {
            return ragContextProvider.retrieveExact(question, snapshot.ragIndexVersion());
        }
        throw new IllegalArgumentException("AI 客服生产 RAG 快照不合法");
    }

    /** 执行不创建会话或消息的单轮客服实验。 */
    public AiChatResponse experimentReply(String question, RagWorkflowContext ragContext,
                                          String experimentRunId, String workflowVersion,
                                          String modelExecutionConfigVersion) {
        return experimentReply(question, ragContext, experimentRunId, workflowVersion,
                modelExecutionConfigVersion, null, null);
    }

    public AiChatResponse experimentReply(String question, RagWorkflowContext ragContext,
                                          String experimentRunId, String workflowVersion,
                                          String modelExecutionConfigVersion,
                                          String evaluationTaskId, String idempotencyKey) {
        List<AiMessage> messages = new ArrayList<>();
        messages.add(message(AiRole.SYSTEM, systemPrompt));
        if (ragContext.present()) messages.add(message(AiRole.SYSTEM, ragContext.text()));
        messages.add(message(AiRole.USER, question));
        String taskId = "experiment:" + experimentRunId;
        AiCallContext context = new AiCallContext(
                "ai-assistant-service", AiFeatureCode.AI_ASSISTANT,
                AiOperationCode.EXPERIMENT_ASSISTANT, taskId, workflowVersion,
                "PROMPT_ASSISTANT_CHAT_0001", modelExecutionConfigVersion,
                evaluationTaskId, ragContext.ragIndexVersion(), AiCallPriority.P3,
                idempotencyKey == null ? "assistant:" + taskId : idempotencyKey,
                Instant.now().plusSeconds(240));
        AiChatResponse response = aiClient.chat(new AiChatRequest(
                AiModality.TEXT, context, messages, 1500, 0.2, AiResponseFormat.TEXT, false));
        if (response == null || response.content() == null || response.content().isBlank()) {
            throw new IllegalArgumentException("AI 网关未返回客服实验回复");
        }
        return response;
    }

    private AiMessage message(AiRole role, String content) {
        return new AiMessage(role, List.of(new AiContentPart(AiContentType.TEXT, content, null)));
    }
}
