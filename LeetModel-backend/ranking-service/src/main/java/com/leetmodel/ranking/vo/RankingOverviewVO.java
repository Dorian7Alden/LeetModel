package com.leetmodel.ranking.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class RankingOverviewVO {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long problemId;

    private String batchId;
    private LocalDateTime computedAt;
    private Integer total;
    private List<RankingEntryVO> items;
}
