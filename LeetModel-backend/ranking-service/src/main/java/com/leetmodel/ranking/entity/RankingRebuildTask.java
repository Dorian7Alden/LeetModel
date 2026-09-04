package com.leetmodel.ranking.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.leetmodel.common.core.bean.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** 按题目合并的排行重建任务。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ranking_rebuild_task")
public class RankingRebuildTask extends BaseEntity {
    private Long problemId;
    private String status;
    private Long requestedRevision;
    private Long runningRevision;
    private Long completedRevision;
    private String sourceFingerprint;
    private String traceId;
    private Integer retryCount;
    private LocalDateTime nextRunAt;
    private String leaseOwner;
    private String leaseToken;
    private LocalDateTime leaseExpiresAt;
    private LocalDateTime heartbeatAt;
    private Integer recoveryCount;
    private String lastError;
}
