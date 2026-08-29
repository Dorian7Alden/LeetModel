package com.leetmodel.evaluation.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.leetmodel.common.core.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("evaluation_sample")
public class EvaluationSample extends BaseEntity {
    private Long datasetId;
    private String sampleType;
    private String payloadSchemaVersion;
    private String payloadJson;
    private Long submissionId;
    private Long teamId;
    private Long problemId;
    private Integer sortOrder;
    private String note;
}
