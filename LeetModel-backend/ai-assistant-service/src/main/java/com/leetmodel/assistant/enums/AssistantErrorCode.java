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
    MESSAGE_NOT_FAILED(40504, "只有失败的 AI 回复可以重试"),
    PRODUCTION_CONFIG_UNAVAILABLE(40505, "AI 客服生产配置不存在或不可用"),
    PRODUCTION_CHANGE_INVALID(40506, "AI 客服生产配置变更请求不合法"),
    PRODUCTION_CHANGE_CONFLICT(40507, "生产配置已经变化，请刷新后重新确认"),
    PRODUCTION_DEPENDENCY_UNAVAILABLE(50501, "AI 客服目标配置依赖暂不可用");

    private final int code;
    private final String message;
}
