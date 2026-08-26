package com.leetmodel.ranking.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.leetmodel.common.core.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ranking_snapshot")
public class RankingSnapshot extends BaseEntity {
    private String batchId;
    private Long problemId;
    private Long teamId;
    private String teamName;
    private Long submissionId;
    private Long reviewTaskId;
    private String workflowVersion;
    private BigDecimal score;
    private Integer rankNo;
    private LocalDateTime submittedAt;
    private LocalDateTime reviewFinishedAt;
    private LocalDateTime computedAt;
    private Integer currentMarker;
}
