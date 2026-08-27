package com.leetmodel.ranking.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class TeamRankingContextVO {
    private RankingEntryVO current;
    private List<RankingEntryVO> nearby;
    private Integer total;
}
