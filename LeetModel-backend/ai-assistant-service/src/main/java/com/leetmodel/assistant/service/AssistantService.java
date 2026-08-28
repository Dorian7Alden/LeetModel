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
import com.leetmodel.assistant.workflow.AssistantWorkflow;
import com.leetmodel.common.ai.model.AiChatResponse;
import com.leetmodel.common.api.dto.AssistantConversationSummaryDTO;
import com.leetmodel.common.api.dto.ProblemOptionDTO;
import com.leetmodel.common.api.feign.ProblemFeignClient;
import com.leetmodel.common.core.exception.BusinessException;
import com.leetmodel.common.core.result.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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

    /**
     * 创建当前用户的会话。
     *
     * @param userId 当前用户
     * @param title 可选标题
     * @return 新会话
     */
    public ConversationVO createConversation(Long userId, String title) {
        LocalDateTime now = LocalDateTime.now();
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
     * 查询会话和完整消息历史。
     */
    public ConversationVO getConversation(Long conversationId, Long userId) {
        AssistantConversation conversation = requiredOwnedConversation(conversationId, userId);
        return toConversation(conversation, listMessages(conversationId));
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
            ReplyClaim claim = createProcessingReply(conversation, userMessage);
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
            if (workflow.needsProblemTool(userMessage.getContent())) {
                Result<List<ProblemOptionDTO>> response = problemFeignClient.getPublishedOptions(null, 8);
                if (response == null || !response.isSuccess() || response.getData() == null) {
                    throw new IllegalStateException("题目查询服务暂不可用");
                }
                candidates = response.getData();
                toolContextJson = objectMapper.writeValueAsString(candidates);
            }
            AiChatResponse response = workflow.reply(recentCompletedMessages(conversation.getId()),
                    userMessage, candidates);
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
                                             AssistantMessage userMessage) {
        LocalDateTime now = LocalDateTime.now();
        AssistantMessage reply = new AssistantMessage();
        reply.setConversationId(conversation.getId());
        reply.setUserId(conversation.getUserId());
        reply.setReplyToMessageId(userMessage.getId());
        reply.setRole("ASSISTANT");
        reply.setStatus("PROCESSING");
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
                .content(message.getContent())
                .errorMessage(message.getErrorMessage())
                .modelName(message.getModelName())
                .aiCallId(message.getAiCallId())
                .usedProblemTool(message.getToolContextJson() != null)
                .createTime(message.getCreateTime())
                .build();
    }

    private String userFacingError(Exception exception) {
        String message = exception.getMessage();
        if ("题目查询服务暂不可用".equals(message)
                || "AI 网关未返回客服回复".equals(message)) {
            return message;
        }
        return "AI 客服暂时无法回答，请稍后重试";
    }

    private record ReplyClaim(AssistantMessage reply, boolean claimed) {
    }
}
