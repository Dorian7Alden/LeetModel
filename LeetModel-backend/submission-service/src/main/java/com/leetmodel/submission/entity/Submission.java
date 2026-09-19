package com.leetmodel.submission.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.leetmodel.common.core.bean.BaseEntity;
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
    /** 正式论文文件资产 ID，物理对象与访问策略由 file-service 管理 */
    private Long fileId;
    private Long fileSize;
    private String status;
}
