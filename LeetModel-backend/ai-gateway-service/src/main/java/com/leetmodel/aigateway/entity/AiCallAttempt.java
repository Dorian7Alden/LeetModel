package com.leetmodel.aigateway.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.leetmodel.common.core.bean.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_call_attempt")
public class AiCallAttempt extends BaseEntity {
    private String attemptId;
    private String taskId;
    private Integer attemptNo;
    private String state;
    private String owner;
    private String newApiRequestId;
    private Integer httpStatus;
    private String errorCode;
    private Long retryAfterMs;
    private LocalDateTime preparedAt;
    private LocalDateTime sentAt;
    private LocalDateTime acknowledgedAt;
    private LocalDateTime finishedAt;
}
