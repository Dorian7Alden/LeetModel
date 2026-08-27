package com.leetmodel.submission.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.leetmodel.common.core.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("submission")
public class Submission extends BaseEntity {
    private Long teamId;
    private Long problemId;
    private Long submitterId;
    private Integer version;
    private String originalFilename;
    private String objectName;
    private Long fileSize;
    private String status;
}
