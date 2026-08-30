package com.leetmodel.common.api.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 按题目聚合的成功提交数量。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProblemSubmissionStatsDTO {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long problemId;
    private Long submissionCount;
}
