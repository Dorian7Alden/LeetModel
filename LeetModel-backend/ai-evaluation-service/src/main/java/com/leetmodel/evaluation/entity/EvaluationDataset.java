package com.leetmodel.evaluation.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.leetmodel.common.core.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("evaluation_dataset")
public class EvaluationDataset extends BaseEntity {
    private String featureCode;
    private String datasetVersion;
    private String sampleSchemaVersion;
    private String name;
    private String description;
    private String status;
    private Integer sampleCount;
    private Long createdBy;
}
