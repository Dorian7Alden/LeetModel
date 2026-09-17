package com.leetmodel.common.security.constant;

import lombok.Getter;

/**
 * RBAC 角色枚举。
 *
 * <p>定义全平台基础角色标识：USER（普通用户）、VIP（付费会员）、ADMIN（系统管理员）。
 * 编码作为 @SaCheckRole 注解的匹配标准。</p>
 */
@Getter
public enum RoleEnum {

    USER("user", "普通用户"),   // 注册默认角色，可使用所有公开基础功能
    VIP("vip", "VIP 用户"),     // 付费进阶角色，拥有论文改进建议等高级权益
    ADMIN("admin", "管理员"),   // 平台管理角色，拥有管理后台全量治理权限
    ;

    /** 角色标识编码，用于 @SaCheckRole 鉴权匹配 */
    private final String code;

    /** 角色中文描述信息 */
    private final String description;

    /**
     * 构造角色枚举。
     *
     * @param code        角色唯一编码字符串
     * @param description 角色中文描述
     */
    RoleEnum(String code, String description) {
        this.code = code;
        this.description = description;
    }
}
