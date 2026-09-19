package com.leetmodel.problem.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 题目附件实体。
 */
@Data
@TableName("problem_attachment")
public class ProblemAttachment {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long problemId;

    /** 文件资产 ID，物理对象与访问策略由 file-service 管理 */
    private Long fileId;

    private String fileName;

    private String contentType;

    private Long fileSize;

    private String description;

    private Integer sortOrder;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
