package com.senior.leetmodelbackend.pojo.enums.error;

import lombok.Getter;

/**
 * 组队匹配模块状态码 (04模块)
 * 涵盖建队、招募、申请入队等
 */
@Getter
public enum TeamErrorCode implements BaseErrorCode {

    TEAM_NOT_FOUND(40401, "该团队不存在或已解散"),
    TEAM_ALREADY_FULL(40402, "该团队人数已满"),
    USER_ALREADY_IN_TEAM(40403, "您已经加入了当前赛事的其他团队，无法重复加入"),
    USER_NOT_IN_TEAM(40404, "您不在该团队中，无法执行此操作"),
    TEAM_LEADER_REQUIRED(40405, "仅限队长可执行此操作"),
    RECRUIT_ROLE_MISMATCH(40406, "您的角色与该团队招募的角色需求不匹配"),
    TEAM_APPLY_PENDING(40407, "您的入队申请正在审核中，请勿重复投递");

    private final int code;
    private final String msg;

    TeamErrorCode(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }
}
