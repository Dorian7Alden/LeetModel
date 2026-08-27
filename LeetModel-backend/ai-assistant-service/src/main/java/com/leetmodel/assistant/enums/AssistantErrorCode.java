package com.leetmodel.assistant.enums;

import com.leetmodel.common.core.exception.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum AssistantErrorCode implements ErrorCode {
    CONVERSATION_NOT_FOUND(40501, "AI 客服会话不存在"),
    CONVERSATION_CLOSED(40502, "会话已结束，不能继续发送消息"),
    MESSAGE_NOT_FOUND(40503, "AI 客服消息不存在"),
    MESSAGE_NOT_FAILED(40504, "只有失败的 AI 回复可以重试");

    private final int code;
    private final String message;
}
