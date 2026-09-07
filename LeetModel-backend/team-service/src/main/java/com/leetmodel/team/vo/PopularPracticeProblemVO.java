package com.leetmodel.team.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 热门练习题视图。
 *
 * <p>练习次数以有效队伍绑定题目的次数为准，题目展示信息由 problem-service 提供。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PopularPracticeProblemVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long problemId;

    private Integer problemCode;

    private String problemTitle;

    private Long practiceCount;
}
