package com.leetmodel.ranking.enums;

import com.leetmodel.common.core.exception.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum RankingErrorCode implements ErrorCode {
    TEAM_RANKING_NOT_FOUND(40901, "当前队伍尚未进入该题排行"),
    SOURCE_DATA_INVALID(40902, "排行源数据不完整或互相矛盾"),
    DEPENDENCY_UNAVAILABLE(50901, "排行依赖服务暂不可用");

    private final int code;
    private final String message;
}
