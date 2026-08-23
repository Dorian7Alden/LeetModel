package com.leetmodel.team.enums;

import com.leetmodel.common.core.exception.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 团队模块错误码 —— BB=03。
 */
@Getter
@AllArgsConstructor
public enum TeamErrorCode implements ErrorCode {

    // ---- 4xxxx 客户端错误 ----
    TEAM_NOT_FOUND(40301, "团队不存在"),
    TEAM_FULL(40302, "团队已满"),
    USER_ALREADY_IN_TEAM(40303, "用户已在该团队中"),
    NOT_TEAM_MEMBER(40304, "不是该团队成员"),
    NOT_TEAM_LEADER(40305, "不是队长，无权操作"),
    CANNOT_REMOVE_LEADER(40306, "不能移除队长"),
    LEADER_CANNOT_LEAVE(40307, "队长不能退出团队，请先转让队长或解散团队"),
    TEAM_ALREADY_DISBANDED(40308, "团队已解散"),
    USER_NOT_AVAILABLE(40309, "用户不存在或账号不可用"),
    TEAM_NOT_RECRUITING(40310, "团队当前未开放招募"),
    APPLICATION_ALREADY_PENDING(40311, "已存在待处理的入队申请"),
    APPLICATION_NOT_FOUND(40312, "入队申请不存在"),
    APPLICATION_ALREADY_HANDLED(40313, "入队申请已处理"),
    INVALID_APPLICATION_DECISION(40314, "审核决定只能是 approved 或 rejected"),
    CANNOT_APPLY_OWN_TEAM(40315, "不能申请加入自己创建的团队"),
    INVALID_TEAM_STATUS(40316, "团队状态只能是 0 或 1"),

    // ---- 5xxxx 服务端错误 ----
    ;

    private final int code;
    private final String message;
}
