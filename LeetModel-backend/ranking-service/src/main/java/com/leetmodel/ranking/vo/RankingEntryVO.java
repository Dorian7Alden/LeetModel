package com.leetmodel.ranking.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class RankingEntryVO {
    private Integer rank;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long teamId;

    private String teamName;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long submissionId;

    private BigDecimal score;
    private String workflowVersion;
    private LocalDateTime submittedAt;
    private LocalDateTime reviewFinishedAt;
}
