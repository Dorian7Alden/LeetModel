package com.leetmodel.problem.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.leetmodel.common.core.bean.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("contest")
public class Contest extends BaseEntity {
    private String code;
    private String name;
    private String englishName;
    private String scheduleDesc;
    private String teamRules;
    private String submissionSpec;
    private String problemSpec;
    private String description;
    private String officialUrl;
}
