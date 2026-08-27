package com.leetmodel.assistant.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.leetmodel.common.core.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("assistant_conversation")
public class AssistantConversation extends BaseEntity {
    private Long userId;
    private String title;
    private String status;
}
