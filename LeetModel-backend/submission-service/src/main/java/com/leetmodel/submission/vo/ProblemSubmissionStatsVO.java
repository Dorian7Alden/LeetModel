package com.leetmodel.submission.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 指定题目的成功提交次数统计。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProblemSubmissionStatsVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long problemId;
    private Long submissionCount;
}
