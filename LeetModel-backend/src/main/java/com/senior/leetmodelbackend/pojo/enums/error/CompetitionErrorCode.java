package com.senior.leetmodelbackend.pojo.enums.error;

import lombok.Getter;

/**
 * 全真赛事模拟模块状态码 (03模块)
 * 涵盖赛事报名、比赛状态校验、提交作品等
 */
@Getter
public enum CompetitionErrorCode implements BaseErrorCode {

    COMPETITION_NOT_FOUND(40301, "该赛事不存在"),
    COMPETITION_ENROLL_CLOSED(40302, "报名已截止或报名人数已满"),
    COMPETITION_NOT_STARTED(40303, "赛事尚未开始，无法进行该操作"),
    COMPETITION_ALREADY_ENDED(40304, "赛事已结束，停止作品提交"),
    USER_NOT_ENROLLED(40305, "您尚未报名该赛事，无法进入比赛模拟"),
    COMPETITION_REQUIRE_TEAM(40306, "该赛事仅限团队报名参赛");

    private final int code;
    private final String msg;

    CompetitionErrorCode(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }
}
