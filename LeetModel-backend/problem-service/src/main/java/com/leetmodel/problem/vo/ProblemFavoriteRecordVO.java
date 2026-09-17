package com.leetmodel.problem.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 题目收藏记录 VO。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProblemFavoriteRecordVO {

    /** 题目 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long problemId;

    /** 收藏时间毫秒时间戳，方便前端直接排序 */
    private Long favoritedAt;

    /** 收藏时间 */
    private LocalDateTime createTime;
}
