package com.leetmodel.problem.enums;

import com.leetmodel.common.core.exception.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Problem 服务错误码枚举。
 * <p>
 * 号段 BB=04（题目管理），CC 从 01 递增。
 * </p>
 */
@Getter
@AllArgsConstructor
public enum ProblemErrorCode implements ErrorCode {

    // ---- 4xxxx 客户端错误 ----
    /** 题目不存在 */
    PROBLEM_NOT_FOUND(40401, "题目不存在"),

    /** 标签不存在 */
    TAG_NOT_FOUND(40402, "标签不存在"),

    /** 标签名称已存在 */
    TAG_NAME_DUPLICATE(40403, "标签名称已存在"),

    /** 难度值不合法 */
    INVALID_DIFFICULTY(40405, "难度值不合法，只支持 1-3"),

    /** 题目状态不合法 */
    INVALID_STATUS(40406, "题目状态不合法，只支持 0-3"),

    /** 标签仍被题目使用 */
    TAG_IN_USE(40407, "标签仍被题目使用，不能删除"),
    CONTEST_NOT_FOUND(40408, "赛事不存在"),
    ATTACHMENT_NOT_FOUND(40410, "题目附件不存在"),
    STORAGE_NOT_ENABLED(40411, "附件存储服务未启用"),
    TAG_TYPE_CONFLICT(40412, "背景领域和题目类型分别只能选择一个标签"),
    INVALID_SCORE_RANGE(40413, "最低分不能大于最高分"),
    ;

    /** 错误码 */
    private final int code;

    /** 错误信息 */
    private final String message;
}
