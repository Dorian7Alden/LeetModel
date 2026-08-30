package com.leetmodel.assistant.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("assistant_production_pointer")
public class AssistantProductionPointer {
    @TableId
    private Long id;
    private Long activeConfigId;
    private Long revision;
    private Long activatedBy;
    private LocalDateTime activatedAt;
    private LocalDateTime observationUntil;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
