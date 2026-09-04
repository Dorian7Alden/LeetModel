package com.leetmodel.assistant.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.leetmodel.common.core.bean.BaseEntity;
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
    private String productionConfigVersion;
    private Long productionRevision;
    private String workflowVersion;
    private String promptVersion;
    private String modelExecutionConfigVersion;
    private String toolsetVersion;
    private Integer attemptCount;
    private String ragMode;
    private String ragIndexVersion;
    private String content;
    private String errorMessage;
    private String toolContextJson;
    private String modelName;
    private String aiCallId;
}
