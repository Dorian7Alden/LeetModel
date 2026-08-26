package com.leetmodel.assistant.controller;

import com.leetmodel.assistant.dto.ConversationCreateRequest;
import com.leetmodel.assistant.dto.MessageSendRequest;
import com.leetmodel.assistant.service.AssistantService;
import com.leetmodel.assistant.vo.AssistantMessageVO;
import com.leetmodel.assistant.vo.AssistantReplyVO;
import com.leetmodel.assistant.vo.ConversationVO;
import com.leetmodel.common.core.result.Result;
import com.leetmodel.common.security.context.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api/assistant/conversations")
@RequiredArgsConstructor
@Tag(name = "AI 客服")
public class AssistantController {

    private final AssistantService assistantService;

    @Operation(summary = "创建 AI 客服会话")
    @PostMapping
    public Result<ConversationVO> create(@Valid @RequestBody ConversationCreateRequest request) {
        return Result.ok(assistantService.createConversation(UserContext.getUserId(), request.getTitle()));
    }

    @Operation(summary = "查询当前用户的 AI 客服会话")
    @GetMapping
    public Result<List<ConversationVO>> list() {
        return Result.ok(assistantService.listConversations(UserContext.getUserId()));
    }

    @Operation(summary = "查询 AI 客服会话与消息历史")
    @GetMapping("/{conversationId}")
    public Result<ConversationVO> get(
            @PathVariable @Positive(message = "会话标识必须为正整数") Long conversationId) {
        return Result.ok(assistantService.getConversation(conversationId, UserContext.getUserId()));
    }

    @Operation(summary = "发送消息并获取 AI 客服回复")
    @PostMapping("/{conversationId}/messages")
    public Result<AssistantReplyVO> send(
            @PathVariable @Positive(message = "会话标识必须为正整数") Long conversationId,
            @Valid @RequestBody MessageSendRequest request) {
        return Result.ok(assistantService.send(conversationId, UserContext.getUserId(),
                request.getContent(), request.getClientRequestId()));
    }

    @Operation(summary = "重试失败的 AI 客服回复")
    @PostMapping("/messages/{messageId}/retry")
    public Result<AssistantMessageVO> retry(
            @PathVariable @Positive(message = "消息标识必须为正整数") Long messageId) {
        return Result.ok(assistantService.retry(messageId, UserContext.getUserId()));
    }

    @Operation(summary = "结束 AI 客服会话")
    @PostMapping("/{conversationId}/close")
    public Result<ConversationVO> close(
            @PathVariable @Positive(message = "会话标识必须为正整数") Long conversationId) {
        return Result.ok(assistantService.close(conversationId, UserContext.getUserId()));
    }
}
