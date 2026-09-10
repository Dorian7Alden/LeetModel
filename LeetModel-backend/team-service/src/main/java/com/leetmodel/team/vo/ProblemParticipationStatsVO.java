package com.leetmodel.team.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 指定题目的有效参赛队伍与成员聚合统计。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProblemParticipationStatsVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long problemId;
    private Long teamCount;
    private Long participantCount;
}
