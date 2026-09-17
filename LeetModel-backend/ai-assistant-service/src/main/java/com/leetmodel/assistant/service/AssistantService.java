package com.leetmodel.assistant.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leetmodel.assistant.entity.AssistantConversation;
import com.leetmodel.assistant.entity.AssistantMessage;
import com.leetmodel.assistant.enums.AssistantErrorCode;
import com.leetmodel.assistant.mapper.AssistantConversationMapper;
import com.leetmodel.assistant.mapper.AssistantMessageMapper;
import com.leetmodel.assistant.vo.AssistantMessageVO;
import com.leetmodel.assistant.vo.AssistantReplyVO;
import com.leetmodel.assistant.vo.ConversationVO;
import com.leetmodel.assistant.workflow.AssistantProductionSnapshot;
import com.leetmodel.assistant.workflow.AssistantWorkflow;
import com.leetmodel.assistant.tool.AssistantToolException;
import com.leetmodel.assistant.tool.AssistantToolOrchestrator;
import com.leetmodel.assistant.tool.AssistantToolRunResult;
import com.leetmodel.common.ai.model.AiChatResponse;
import com.leetmodel.common.api.dto.AssistantConversationSummaryDTO;
import com.leetmodel.common.api.dto.ProblemOptionDTO;
import com.leetmodel.common.api.feign.ProblemFeignClient;
import com.leetmodel.common.core.exception.BusinessException;
import com.leetmodel.common.core.exception.ErrorCodeEnum;
import com.leetmodel.common.core.result.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 管理 AI 客服会话归属、消息幂等、只读题目工具和失败重试。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssistantService {

    private static final String DEFAULT_TITLE = "新会话";

    private final AssistantConversationMapper conversationMapper;
    private final AssistantMessageMapper messageMapper;
    private final ProblemFeignClient problemFeignClient;
    private final AssistantWorkflow workflow;
    private final ObjectMapper objectMapper;
    private final AssistantProductionConfigService productionConfigService;
    private final AssistantToolOrchestrator toolOrchestrator;

    /**
     * 创建当前用户的会话。
     *
     * @param userId 当前用户
     * @param title 可选标题
     * @return 新会话
     */
    public ConversationVO createConversation(Long userId, String title) {
        LocalDateTime now = LocalDateTime.now();
        // 自动复用未发问的空白会话（即未发送任何消息的 ACTIVE 会话）
        AssistantConversation blank = findBlankConversation(userId);
        if (blank != null) {
            String targetTitle = (title == null || title.isBlank()) ? DEFAULT_TITLE : title.trim();
            if (!targetTitle.equals(blank.getTitle())) {
                blank.setTitle(targetTitle);
                blank.setUpdateTime(now);
                conversationMapper.updateById(blank);
            }
            return toConversation(blank, List.of());
        }

        AssistantConversation conversation = new AssistantConversation();
        conversation.setUserId(userId);
        conversation.setTitle(title == null || title.isBlank() ? DEFAULT_TITLE : title.trim());
        conversation.setStatus("ACTIVE");
        conversation.setCreateTime(now);
        conversation.setUpdateTime(now);
        conversationMapper.insert(conversation);
        return toConversation(conversation, List.of());
    }

    /**
     * 查询当前用户的会话列表。
     */
    public List<ConversationVO> listConversations(Long userId) {
        return conversationMapper.selectList(new LambdaQueryWrapper<AssistantConversation>()
                        .eq(AssistantConversation::getUserId, userId)
                        .orderByDesc(AssistantConversation::getUpdateTime))
                .stream().map(item -> toConversation(item, List.of())).toList();
    }

    /**
     * 查询会话和完整消息历史（兼容老接口，默认拉取最近50条）。
     */
    public ConversationVO getConversation(Long conversationId, Long userId) {
        return getConversation(conversationId, userId, null, null);
    }

    /**
     * 基于游标分页查询会话及历史消息。
     *
     * @param conversationId 目标会话 ID
     * @param userId         当前所属用户 ID
     * @param cursor         上一页最旧消息 ID，首次拉取传 null
     * @param limit          单页大小，默认 50，最大 100
     * @return 包含逆向游标与更多标识的会话视图
     */
    public ConversationVO getConversation(Long conversationId, Long userId, Long cursor, Integer limit) {
        AssistantConversation conversation = requiredOwnedConversation(conversationId, userId);
        int safeLimit = limit == null ? 50 : Math.max(1, Math.min(limit, 100));

        LambdaQueryWrapper<AssistantMessage> wrapper = new LambdaQueryWrapper<AssistantMessage>()
                .eq(AssistantMessage::getConversationId, conversationId)
                .orderByDesc(AssistantMessage::getId);
        if (cursor != null && cursor > 0) {
            wrapper.lt(AssistantMessage::getId, cursor);
        }
        wrapper.last("LIMIT " + (safeLimit + 1));

        List<AssistantMessage> queryList = messageMapper.selectList(wrapper);
        boolean hasMore = queryList.size() > safeLimit;
        List<AssistantMessage> paged = hasMore ? queryList.subList(0, safeLimit) : queryList;
        Long nextCursor = hasMore && !paged.isEmpty() ? paged.get(paged.size() - 1).getId() : null;

        List<AssistantMessage> chronological = new ArrayList<>(paged);
        Collections.reverse(chronological);

        ConversationVO vo = toConversation(conversation, chronological);
        vo.setNextCursor(nextCursor);
        vo.setHasMore(hasMore);
        return vo;
    }

    /**
     * 软删除指定会话及关联的所有历史消息。
     */
    public void deleteConversation(Long conversationId, Long userId) {
        AssistantConversation conversation = requiredOwnedConversation(conversationId, userId);
        conversationMapper.deleteById(conversation.getId());
        messageMapper.delete(new LambdaQueryWrapper<AssistantMessage>()
                .eq(AssistantMessage::getConversationId, conversationId));
    }

    /**
     * 自定义重命名指定会话标题。
     */
    public ConversationVO renameConversation(Long conversationId, Long userId, String title) {
        AssistantConversation conversation = requiredOwnedConversation(conversationId, userId);
        BusinessException.throwIf(title == null || title.isBlank(), ErrorCodeEnum.PARAM_INVALID);
        conversation.setTitle(title.trim());
        conversation.setUpdateTime(LocalDateTime.now());
        conversationMapper.updateById(conversation);
        return toConversation(conversation, List.of());
    }

    /**
     * 幂等保存用户消息并生成客服回复。AI 或工具失败也会保存为可重试的失败回复。
     */
    public AssistantReplyVO send(Long conversationId, Long userId, String content, String clientRequestId) {
        AssistantConversation conversation = requiredOwnedConversation(conversationId, userId);
        BusinessException.throwIf(!"ACTIVE".equals(conversation.getStatus()),
                AssistantErrorCode.CONVERSATION_CLOSED);

        AssistantMessage userMessage = findUserRequest(conversationId, clientRequestId);
        if (userMessage == null) {
            userMessage = new AssistantMessage();
            userMessage.setConversationId(conversationId);
            userMessage.setUserId(userId);
            userMessage.setClientRequestId(clientRequestId);
            userMessage.setRole("USER");
            userMessage.setStatus("COMPLETED");
            userMessage.setContent(content.trim());
            userMessage.setCreateTime(LocalDateTime.now());
            userMessage.setUpdateTime(userMessage.getCreateTime());
            try {
                messageMapper.insert(userMessage);
            } catch (DuplicateKeyException exception) {
                userMessage = findUserRequest(conversationId, clientRequestId);
                if (userMessage == null) throw exception;
            }
            updateDerivedTitle(conversation, userMessage.getContent());
        }

        AssistantMessage reply = findReply(userMessage.getId());
        if (reply == null) {
            AssistantProductionSnapshot snapshot = productionConfigService.currentSnapshot();
            ReplyClaim claim = createProcessingReply(conversation, userMessage, snapshot);
            reply = claim.reply();
            if (claim.claimed()) {
                reply = generateReply(conversation, userMessage, reply);
            }
        }
        return AssistantReplyVO.builder()
                .userMessage(toMessage(userMessage))
                .assistantMessage(toMessage(reply))
                .build();
    }

    /**
     * 通过 SSE 流式协议发送用户提问并实时推送工具状态与增量文本事件。
     */
    public SseEmitter streamSend(Long conversationId, Long userId, String content, String clientRequestId) {
        SseEmitter emitter = new SseEmitter(180_000L);
        AssistantConversation conversation = requiredOwnedConversation(conversationId, userId);
        BusinessException.throwIf(!"ACTIVE".equals(conversation.getStatus()),
                AssistantErrorCode.CONVERSATION_CLOSED);

        CompletableFuture.runAsync(() -> {
            try {
                AssistantMessage userMessage = findUserRequest(conversationId, clientRequestId);
                if (userMessage == null) {
                    userMessage = new AssistantMessage();
                    userMessage.setConversationId(conversationId);
                    userMessage.setUserId(userId);
                    userMessage.setClientRequestId(clientRequestId);
                    userMessage.setRole("USER");
                    userMessage.setStatus("COMPLETED");
                    userMessage.setContent(content.trim());
                    LocalDateTime now = LocalDateTime.now();
                    userMessage.setCreateTime(now);
                    userMessage.setUpdateTime(now);
                    try {
                        messageMapper.insert(userMessage);
                    } catch (DuplicateKeyException exception) {
                        userMessage = findUserRequest(conversationId, clientRequestId);
                        if (userMessage == null) throw exception;
                    }
                    updateDerivedTitle(conversation, userMessage.getContent());
                }

                AssistantMessage reply = findReply(userMessage.getId());
                if (reply == null) {
                    AssistantProductionSnapshot snapshot = productionConfigService.currentSnapshot();
                    ReplyClaim claim = createProcessingReply(conversation, userMessage, snapshot);
                    reply = claim.reply();
                    if (claim.claimed()) {
                        generateStreamingReply(conversation, userMessage, reply, emitter);
                    }
                } else if ("COMPLETED".equals(reply.getStatus())) {
                    try {
                        emitter.send(SseEmitter.event().name("message_end")
                                .data(Map.of("messageId", reply.getId(),
                                        "status", reply.getStatus(),
                                        "fullContent", reply.getContent(),
                                        "toolContextJson", reply.getToolContextJson() == null ? "" : reply.getToolContextJson())));
                        emitter.complete();
                    } catch (Exception ignored) {}
                }
            } catch (Exception e) {
                log.warn("SSE 异步调度异常: exceptionType={}", e.getClass().getSimpleName());
                try {
                    emitter.send(SseEmitter.event().name("error")
                            .data(Map.of("code", 500, "message", userFacingError(e))));
                    emitter.complete();
                } catch (Exception ignored) {}
            }
        });

        return emitter;
    }

    private AssistantMessage generateStreamingReply(AssistantConversation conversation,
                                                    AssistantMessage userMessage,
                                                    AssistantMessage existingReply,
                                                    SseEmitter emitter) {
        List<ProblemOptionDTO> candidates = null;
        String toolContextJson = null;
        try {
            int attemptNo = beginAttempt(existingReply);
            AssistantProductionSnapshot snapshot = snapshot(existingReply);
            AiChatResponse response;
            if (snapshot.toolsetVersion() != null) {
                try {
                    emitter.send(SseEmitter.event().name("tool_start")
                            .data(Map.of("tool", "assistant_tools", "displayName", "正在调用受控领域工具...", "status", "RUNNING")));
                } catch (Exception ignored) {}
                AssistantToolRunResult toolResult = toolOrchestrator.runStreaming(
                        recentCompletedMessages(conversation.getId()), userMessage,
                        existingReply, snapshot, snapshot.toolsetVersion(), attemptNo,
                        Instant.now().plusSeconds(240), chunk -> sendDelta(emitter, chunk.deltaText()));
                response = toolResult.response();
                toolContextJson = toolResult.toolContextJson();
                try {
                    emitter.send(SseEmitter.event().name("tool_end")
                            .data(Map.of("tool", "assistant_tools", "displayName", "工具执行完成", "status", "COMPLETED",
                                    "toolContextJson", toolContextJson == null ? "" : toolContextJson)));
                } catch (Exception ignored) {}
            } else {
                if (workflow.needsProblemTool(userMessage.getContent())) {
                    try {
                        emitter.send(SseEmitter.event().name("tool_start")
                                .data(Map.of("tool", "search_problem", "displayName", "正在检索题目事实...", "status", "RUNNING")));
                    } catch (Exception ignored) {}
                    Result<List<ProblemOptionDTO>> candidateResponse =
                            problemFeignClient.getPublishedOptions(null, 8);
                    candidates = candidateResponse != null ? candidateResponse.getData() : List.of();
                    toolContextJson = objectMapper.writeValueAsString(candidates);
                    try {
                        emitter.send(SseEmitter.event().name("tool_end")
                                .data(Map.of("tool", "search_problem", "displayName", "已获取相关题目候选", "status", "COMPLETED",
                                        "toolContextJson", toolContextJson)));
                    } catch (Exception ignored) {}
                }
                response = workflow.streamReply(recentCompletedMessages(conversation.getId()),
                        userMessage, candidates, snapshot,
                        chunk -> sendDelta(emitter, chunk.deltaText()));
            }

            AssistantMessage completed = persistReply(existingReply, conversation, userMessage, "COMPLETED",
                    response.content(), null, toolContextJson, response.model(), response.callId());
            try {
                emitter.send(SseEmitter.event().name("message_end")
                        .data(Map.of("messageId", completed.getId(),
                                "status", "COMPLETED",
                                "fullContent", completed.getContent() == null ? "" : completed.getContent(),
                                "toolContextJson", completed.getToolContextJson() == null ? "" : completed.getToolContextJson())));
                emitter.complete();
            } catch (Exception ignored) {}
            return completed;
        } catch (Exception exception) {
            log.warn("assistant-chat stream status=FAILED conversationId={} errorType={}",
                    conversation.getId(), exception.getClass().getSimpleName());
            AssistantMessage failed = persistReply(existingReply, conversation, userMessage, "FAILED",
                    null, userFacingError(exception), toolContextJson, null, null);
            try {
                emitter.send(SseEmitter.event().name("error")
                        .data(Map.of("code", 500, "message", userFacingError(exception))));
                emitter.complete();
            } catch (Exception ignored) {}
            return failed;
        }
    }

    private void sendDelta(SseEmitter emitter, String content) {
        if (content == null || content.isEmpty()) return;
        try {
            emitter.send(SseEmitter.event().name("delta").data(Map.of("content", content)));
        } catch (Exception ignored) {}
    }

    /**
     * 重试一条失败的客服回复。
     */
    public AssistantMessageVO retry(Long messageId, Long userId) {
        AssistantMessage reply = messageMapper.selectById(messageId);
        BusinessException.throwIf(reply == null || !"ASSISTANT".equals(reply.getRole()),
                AssistantErrorCode.MESSAGE_NOT_FOUND);
        AssistantConversation conversation = requiredOwnedConversation(reply.getConversationId(), userId);
        BusinessException.throwIf(!"ACTIVE".equals(conversation.getStatus()),
                AssistantErrorCode.CONVERSATION_CLOSED);
        BusinessException.throwIf(!"FAILED".equals(reply.getStatus()), AssistantErrorCode.MESSAGE_NOT_FAILED);
        int claimed = messageMapper.claimRetry(messageId, LocalDateTime.now());
        BusinessException.throwIf(claimed == 0, AssistantErrorCode.MESSAGE_NOT_FAILED);
        AssistantMessage userMessage = messageMapper.selectById(reply.getReplyToMessageId());
        BusinessException.throwIf(userMessage == null || !"USER".equals(userMessage.getRole()),
                AssistantErrorCode.MESSAGE_NOT_FOUND);
        return toMessage(generateReply(conversation, userMessage, reply));
    }

    /**
     * 结束会话。重复结束保持幂等。
     */
    public ConversationVO close(Long conversationId, Long userId) {
        AssistantConversation conversation = requiredOwnedConversation(conversationId, userId);
        if (!"CLOSED".equals(conversation.getStatus())) {
            conversation.setStatus("CLOSED");
            conversation.setUpdateTime(LocalDateTime.now());
            conversationMapper.updateById(conversation);
        }
        return toConversation(conversation, listMessages(conversationId));
    }

    /**
     * 恢复因进程中断而停留在重试中的消息。
     */
    @Scheduled(fixedDelayString = "${assistant.recovery-delay-ms:60000}")
    public void recoverStaleRetries() {
        LocalDateTime now = LocalDateTime.now();
        messageMapper.recoverStaleRetries(now.minusMinutes(5), now);
    }

    /**
     * 获取会话总数。
     */
    public long countConversations() {
        return conversationMapper.selectCount(null);
    }

    /**
     * 获取最近会话摘要供管理端聚合。
     */
    public List<AssistantConversationSummaryDTO> listRecent(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        return conversationMapper.selectList(new LambdaQueryWrapper<AssistantConversation>()
                        .orderByDesc(AssistantConversation::getUpdateTime)
                        .last("LIMIT " + safeLimit))
                .stream().map(conversation -> new AssistantConversationSummaryDTO(
                        conversation.getId(), conversation.getUserId(), conversation.getTitle(),
                        conversation.getStatus(), messageMapper.selectCount(
                                new LambdaQueryWrapper<AssistantMessage>()
                                        .eq(AssistantMessage::getConversationId, conversation.getId())),
                        conversation.getCreateTime(), conversation.getUpdateTime()))
                .toList();
    }

    private AssistantMessage generateReply(AssistantConversation conversation,
                                            AssistantMessage userMessage,
                                            AssistantMessage existingReply) {
        List<ProblemOptionDTO> candidates = null;
        String toolContextJson = null;
        try {
            int attemptNo = beginAttempt(existingReply);
            AssistantProductionSnapshot snapshot = snapshot(existingReply);
            AiChatResponse response;
            if (snapshot.toolsetVersion() != null) {
                AssistantToolRunResult toolResult = toolOrchestrator.run(
                        recentCompletedMessages(conversation.getId()), userMessage,
                        existingReply, snapshot, snapshot.toolsetVersion(), attemptNo,
                        Instant.now().plusSeconds(240));
                response = toolResult.response();
                toolContextJson = toolResult.toolContextJson();
            } else {
                if (workflow.needsProblemTool(userMessage.getContent())) {
                    Result<List<ProblemOptionDTO>> candidateResponse =
                            problemFeignClient.getPublishedOptions(null, 8);
                    if (candidateResponse == null || !candidateResponse.isSuccess()
                            || candidateResponse.getData() == null) {
                        throw new IllegalStateException("题目查询服务暂不可用");
                    }
                    candidates = candidateResponse.getData();
                    toolContextJson = objectMapper.writeValueAsString(candidates);
                }
                response = workflow.reply(recentCompletedMessages(conversation.getId()),
                        userMessage, candidates, snapshot);
            }
            return persistReply(existingReply, conversation, userMessage, "COMPLETED",
                    response.content(), null, toolContextJson, response.model(), response.callId());
        } catch (Exception exception) {
            log.warn("assistant-chat status=FAILED conversationId={} errorType={}",
                    conversation.getId(), exception.getClass().getSimpleName());
            return persistReply(existingReply, conversation, userMessage, "FAILED",
                    null, userFacingError(exception), toolContextJson, null, null);
        }
    }

    private ReplyClaim createProcessingReply(AssistantConversation conversation,
                                             AssistantMessage userMessage,
                                             AssistantProductionSnapshot snapshot) {
        LocalDateTime now = LocalDateTime.now();
        AssistantMessage reply = new AssistantMessage();
        reply.setConversationId(conversation.getId());
        reply.setUserId(conversation.getUserId());
        reply.setReplyToMessageId(userMessage.getId());
        reply.setRole("ASSISTANT");
        reply.setStatus("PROCESSING");
        reply.setProductionConfigVersion(snapshot.productionConfigVersion());
        reply.setProductionRevision(snapshot.productionRevision());
        reply.setWorkflowVersion(snapshot.workflowVersion());
        reply.setPromptVersion(snapshot.promptVersion());
        reply.setModelExecutionConfigVersion(snapshot.modelExecutionConfigVersion());
        reply.setToolsetVersion(snapshot.toolsetVersion());
        reply.setAttemptCount(0);
        reply.setRagMode(snapshot.ragMode());
        reply.setRagIndexVersion(snapshot.ragIndexVersion());
        reply.setCreateTime(now);
        reply.setUpdateTime(now);
        try {
            messageMapper.insert(reply);
            return new ReplyClaim(reply, true);
        } catch (DuplicateKeyException exception) {
            AssistantMessage existing = findReply(userMessage.getId());
            if (existing == null) throw exception;
            return new ReplyClaim(existing, false);
        }
    }

    private AssistantMessage persistReply(AssistantMessage existing,
                                          AssistantConversation conversation,
                                          AssistantMessage userMessage,
                                          String status, String content, String error,
                                          String toolContextJson, String modelName, String aiCallId) {
        LocalDateTime now = LocalDateTime.now();
        AssistantMessage reply = existing == null ? new AssistantMessage() : existing;
        reply.setConversationId(conversation.getId());
        reply.setUserId(conversation.getUserId());
        reply.setReplyToMessageId(userMessage.getId());
        reply.setRole("ASSISTANT");
        reply.setStatus(status);
        reply.setContent(content);
        reply.setErrorMessage(error);
        reply.setToolContextJson(toolContextJson);
        reply.setModelName(modelName);
        reply.setAiCallId(aiCallId);
        if (existing == null) {
            reply.setCreateTime(now);
            reply.setUpdateTime(now);
            messageMapper.insert(reply);
        } else if ("COMPLETED".equals(status)) {
            messageMapper.complete(reply.getId(), content, toolContextJson, modelName, aiCallId, now);
        } else {
            messageMapper.fail(reply.getId(), error, toolContextJson, now);
        }
        conversation.setUpdateTime(now);
        conversationMapper.updateById(conversation);
        return reply;
    }

    private List<AssistantMessage> recentCompletedMessages(Long conversationId) {
        List<AssistantMessage> descending = new ArrayList<>(messageMapper.selectList(
                new LambdaQueryWrapper<AssistantMessage>()
                        .eq(AssistantMessage::getConversationId, conversationId)
                        .eq(AssistantMessage::getStatus, "COMPLETED")
                        .in(AssistantMessage::getRole, List.of("USER", "ASSISTANT"))
                        .orderByDesc(AssistantMessage::getCreateTime)
                        .orderByDesc(AssistantMessage::getId)
                        .last("LIMIT 20")));
        Collections.reverse(descending);
        return descending;
    }

    private List<AssistantMessage> listMessages(Long conversationId) {
        return messageMapper.selectList(new LambdaQueryWrapper<AssistantMessage>()
                .eq(AssistantMessage::getConversationId, conversationId)
                .orderByAsc(AssistantMessage::getCreateTime)
                .orderByAsc(AssistantMessage::getId));
    }

    private AssistantConversation requiredOwnedConversation(Long id, Long userId) {
        AssistantConversation conversation = conversationMapper.selectOne(
                new LambdaQueryWrapper<AssistantConversation>()
                        .eq(AssistantConversation::getId, id)
                        .eq(AssistantConversation::getUserId, userId)
                        .last("LIMIT 1"));
        BusinessException.throwIf(conversation == null, AssistantErrorCode.CONVERSATION_NOT_FOUND);
        return conversation;
    }

    private AssistantConversation findBlankConversation(Long userId) {
        List<AssistantConversation> activeConvs = conversationMapper.selectList(
                new LambdaQueryWrapper<AssistantConversation>()
                        .eq(AssistantConversation::getUserId, userId)
                        .eq(AssistantConversation::getStatus, "ACTIVE")
                        .orderByDesc(AssistantConversation::getCreateTime));
        for (AssistantConversation conv : activeConvs) {
            Long count = messageMapper.selectCount(
                    new LambdaQueryWrapper<AssistantMessage>()
                            .eq(AssistantMessage::getConversationId, conv.getId()));
            if (count == null || count == 0) {
                return conv;
            }
        }
        return null;
    }

    private AssistantMessage findUserRequest(Long conversationId, String clientRequestId) {
        return messageMapper.selectOne(new LambdaQueryWrapper<AssistantMessage>()
                .eq(AssistantMessage::getConversationId, conversationId)
                .eq(AssistantMessage::getClientRequestId, clientRequestId)
                .eq(AssistantMessage::getRole, "USER")
                .last("LIMIT 1"));
    }

    private AssistantMessage findReply(Long userMessageId) {
        return messageMapper.selectOne(new LambdaQueryWrapper<AssistantMessage>()
                .eq(AssistantMessage::getReplyToMessageId, userMessageId)
                .eq(AssistantMessage::getRole, "ASSISTANT")
                .orderByDesc(AssistantMessage::getCreateTime)
                .last("LIMIT 1"));
    }

    private void updateDerivedTitle(AssistantConversation conversation, String content) {
        if (DEFAULT_TITLE.equals(conversation.getTitle())) {
            String title = content.length() <= 30 ? content : content.substring(0, 30) + "…";
            conversation.setTitle(title);
            conversation.setUpdateTime(LocalDateTime.now());
            conversationMapper.updateById(conversation);
        }
    }

    private ConversationVO toConversation(AssistantConversation conversation,
                                          List<AssistantMessage> messages) {
        return ConversationVO.builder()
                .id(conversation.getId())
                .title(conversation.getTitle())
                .status(conversation.getStatus())
                .createTime(conversation.getCreateTime())
                .updateTime(conversation.getUpdateTime())
                .messages(messages.stream().map(this::toMessage).toList())
                .build();
    }

    private AssistantMessageVO toMessage(AssistantMessage message) {
        return AssistantMessageVO.builder()
                .id(message.getId())
                .replyToMessageId(message.getReplyToMessageId())
                .role(message.getRole())
                .status(message.getStatus())
                .productionConfigVersion(message.getProductionConfigVersion())
                .productionRevision(message.getProductionRevision())
                .workflowVersion(message.getWorkflowVersion())
                .promptVersion(message.getPromptVersion())
                .modelExecutionConfigVersion(message.getModelExecutionConfigVersion())
                .toolsetVersion(message.getToolsetVersion())
                .attemptCount(message.getAttemptCount())
                .ragMode(message.getRagMode())
                .ragIndexVersion(message.getRagIndexVersion())
                .content(message.getContent())
                .errorMessage(message.getErrorMessage())
                .modelName(message.getModelName())
                .aiCallId(message.getAiCallId())
                .usedTool(message.getToolContextJson() != null)
                .usedProblemTool(usedProblemTool(message))
                .createTime(message.getCreateTime())
                .build();
    }

    private AssistantProductionSnapshot snapshot(AssistantMessage reply) {
        BusinessException.throwIf(reply.getProductionConfigVersion() == null
                        || reply.getProductionRevision() == null
                        || reply.getWorkflowVersion() == null
                        || reply.getPromptVersion() == null
                        || reply.getModelExecutionConfigVersion() == null
                        || reply.getRagMode() == null,
                AssistantErrorCode.PRODUCTION_CONFIG_UNAVAILABLE);
        boolean toolWorkflow = reply.getWorkflowVersion().startsWith("ASSISTANT_TOOLS_");
        BusinessException.throwIf(toolWorkflow != (reply.getToolsetVersion() != null),
                AssistantErrorCode.PRODUCTION_CONFIG_UNAVAILABLE);
        return new AssistantProductionSnapshot(reply.getProductionConfigVersion(),
                reply.getProductionRevision(), reply.getWorkflowVersion(),
                reply.getPromptVersion(), reply.getModelExecutionConfigVersion(),
                reply.getToolsetVersion(), reply.getRagMode(), reply.getRagIndexVersion());
    }

    /** 原子增加回复尝试次数并返回本次不可变 attemptNo。 */
    private int beginAttempt(AssistantMessage reply) {
        LocalDateTime now = LocalDateTime.now();
        if (messageMapper.beginAttempt(reply.getId(), now) != 1) {
            throw new IllegalStateException("AI 客服回复未取得生成执行权");
        }
        int attemptNo = (reply.getAttemptCount() == null ? 0 : reply.getAttemptCount()) + 1;
        reply.setAttemptCount(attemptNo);
        reply.setUpdateTime(now);
        return attemptNo;
    }

    /** 判断消息是否使用题目工具，同时兼容旧关键词预取上下文。 */
    private boolean usedProblemTool(AssistantMessage message) {
        String context = message.getToolContextJson();
        if (context == null) return false;
        if (message.getToolsetVersion() == null) return true;
        return context.contains("\"name\":\"search_problem\"")
                || context.contains("\"name\":\"recommend_problem\"");
    }

    private String userFacingError(Exception exception) {
        if (exception instanceof AssistantToolException toolException) {
            String code = toolException.getErrorCode();
            if ("TOOL_TIMEOUT".equals(code)) {
                return "查询题目信息耗时较长，工具调用超时。建议您直接前往平台的「题库」页面，在搜索框中输入题目编号进行快速查看。";
            }
            if ("TOOL_ARGUMENT_INVALID".equals(code)) {
                return "抱歉，未能精确识别该题号或查询参数格式。建议您检查题号是否正确，或直接前往平台「题库」页面按编号筛选查看。";
            }
            if ("TOOL_CALL_LIMIT".equals(code) || "TOOL_NO_PROGRESS".equals(code)) {
                return "抱歉，本次题目事实检索未取得明确进展。您可以直接前往「题库」页面查看详情，或换一种方式提问（如提供题目标题关键词）。";
            }
            if ("PROBLEM_SERVICE_UNAVAILABLE".equals(code) || (toolException.getMessage() != null && toolException.getMessage().contains("题目查询服务"))) {
                return "题目信息查询服务响应异常，暂无法获取该题详细数据。请您前往平台「题库」页面直接浏览题面，或稍后重试。";
            }
            return "AI 客服在查询题目相关事实时遇到临时问题。建议您前往平台「题库」页面按题号查看，或稍后重试。";
        }
        String message = exception.getMessage();
        if (message != null) {
            if (message.contains("题目查询服务暂不可用")) {
                return "题目查询服务当前不可用，暂无法为您检索该题目信息。建议您直接在平台「题库」页面查看对应题号。";
            }
            if (message.contains("AI 网关未返回客服回复")) {
                return "AI 网关响应异常，未生成有效回复，请稍后重试。";
            }
            if (message.contains("connection refused") || message.contains("Timeout")) {
                return "网络连接异常或上游服务超时，建议您稍后重试，或直接通过顶部导航进入相应页面查看。";
            }
        }
        return "抱歉，由于临时服务波动未能为您完成查询。建议您前往平台「题库」页面搜索该题号查看详情，也可以稍后再次向我提问。";
    }

    private record ReplyClaim(AssistantMessage reply, boolean claimed) {
    }
}
