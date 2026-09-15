package com.leetmodel.common.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import java.time.LocalDateTime;

/**
 * 管理聚合使用的 AI 客服会话摘要。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AssistantConversationSummaryDTO {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long conversationId;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;
    private String title;
    private String status;
    private Long messageCount;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
