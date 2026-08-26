package com.leetmodel.review.entity;
import com.baomidou.mybatisplus.annotation.TableName;
import com.leetmodel.common.core.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
@Data @EqualsAndHashCode(callSuper = true) @TableName("review_version")
public class ReviewVersion extends BaseEntity {
    private String versionCode; private String name; private String description;
    private String processSummary; private String finalContractVersion; private String status;
}
