package com.senior.leetmodelbackend.pojo.enums.error;

import lombok.Getter;

/**
 * 平台管理模块状态码 (07模块)
 * 涵盖后台运营、管理员操作校验等
 */
@Getter
public enum AdminErrorCode implements BaseErrorCode {

    ADMIN_TARGET_IS_ADMIN(40701, "无权对其他管理员账号执行封禁或删除操作"),
    REVIEW_STATE_INVALID(40702, "该内容的审核状态不合法或已被审核过，无法重复操作"),
    SYSTEM_CONFIG_INVALID(40703, "提交的系统配置参数不符合要求");

    private final int code;
    private final String msg;

    AdminErrorCode(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }
}
