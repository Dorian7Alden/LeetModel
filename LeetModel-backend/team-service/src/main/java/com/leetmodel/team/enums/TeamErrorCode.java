package com.leetmodel.team.enums;

import com.leetmodel.common.core.exception.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 团队模块业务错误码：BB=03。
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
    PROBLEM_NOT_AVAILABLE(40317, "题目不存在或未发布"),
    USER_HAS_ACTIVE_PROBLEM_TEAM(40318, "用户已在该题目下参加未结束队伍"),
    ROLES_NOT_COVERED(40319, "建模、编程和论文职责必须全部覆盖"),
    PRACTICE_ALREADY_STARTED(40320, "练习已经开始"),
    PRACTICE_NOT_IN_PROGRESS(40321, "练习未在进行中"),
    TEAM_NAME_LOCKED(40322, "练习开始后不能修改队伍名称"),
    TEAM_OPERATION_NOT_ALLOWED(40323, "当前队伍状态不允许执行该操作"),
    LEADER_SUBMISSION_PERMISSION_FIXED(40324, "队长始终拥有提交权限"),
    RECRUITMENT_ROLE_REQUIRED(40325, "每条招募至少选择一个职位"),
    TEAM_SLOT_FULL(40326, "成员与开放招募位置已达到队伍人数上限"),
    RECRUITMENT_NOT_FOUND(40327, "招募信息不存在"),
    RECRUITMENT_ALREADY_CLOSED(40328, "招募信息已经关闭"),

    // ---- 5xxxx 服务端错误 ----
    ;

    private final int code;
    private final String message;
}
