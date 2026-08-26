package com.leetmodel.assistant.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.leetmodel.common.core.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("assistant_message")
public class AssistantMessage extends BaseEntity {
    private Long conversationId;
    private Long userId;
    private String clientRequestId;
    private Long replyToMessageId;
    private String role;
    private String status;
    private String content;
    private String errorMessage;
    private String toolContextJson;
    private String modelName;
    private String aiCallId;
}
