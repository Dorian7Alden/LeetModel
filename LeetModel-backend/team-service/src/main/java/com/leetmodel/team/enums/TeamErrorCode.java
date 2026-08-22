package com.leetmodel.team.enums;

import com.leetmodel.common.core.exception.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 团队模块错误码 —— BB=03。
 *
 * @author LeetModel
 */
@Getter
@AllArgsConstructor
public enum TeamErrorCode implements ErrorCode {

    // ---- 4xxxx 客户端错误 ----
    TEAM_NOT_FOUND(40301, "团队不存在"),
    TEAM_FULL(40302, "团队已满"),
    USER_ALREADY_IN_TEAM(40303, "用户已在其他团队中"),
    NOT_TEAM_MEMBER(40304, "不是该团队成员"),
    NOT_TEAM_LEADER(40305, "不是队长，无权操作"),
    CANNOT_REMOVE_LEADER(40306, "不能移除队长"),
    LEADER_CANNOT_LEAVE(40307, "队长不能退出团队，请先转让队长或解散团队"),
    TEAM_ALREADY_DISBANDED(40308, "团队已解散"),

    // ---- 5xxxx 服务端错误 ----
    ;

    private final int code;
    private final String message;
}
