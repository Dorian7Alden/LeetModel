package com.senior.leetmodelbackend.entity.enums.error;

import lombok.Getter;

/**
 * 题库与专项训练模块状态码 (02模块)
 * 涵盖题目查询、解答提交、专项训练角色匹配等
 */
@Getter
public enum ProblemErrorCode implements BaseErrorCode {

    PROBLEM_NOT_FOUND(40201, "题目不存在"),
    PROBLEM_OFFLINE(40202, "该题目未上架或已下线"),
    PROBLEM_ROLE_MISMATCH(40203, "训练角色不匹配，无法进行该专项训练"),
    SUBMISSION_CONTENT_BLANK(40204, "提交的解答内容不能为空"),
    UNSUPPORTED_PROGRAM_LANG(40205, "系统暂不支持该编程语言"),
    PROBLEM_ALREADY_SOLVED(40206, "该题目已经解答通过，请勿重复提交");

    private final int code;
    private final String msg;

    ProblemErrorCode(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }
}
