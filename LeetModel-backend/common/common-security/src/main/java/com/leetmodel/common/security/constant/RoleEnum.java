package com.leetmodel.common.security.constant;

import lombok.Getter;

/**
 * RBAC 角色枚举 —— 权限体系的基础常量。
 *
 * <p>角色层级（从低到高）：USER → VIP → ADMIN。
 * 每个角色对应的功能范围在需求文档中定义。
 * </p>
 *
 * <p>注意：枚举不能使用 @AllArgsConstructor，需手写构造器。</p>
 *
 * @author LeetModel
 */
@Getter
public enum RoleEnum {

    /** 普通用户：注册默认角色，可使用所有基础功能 */
    USER("user", "普通用户"),

    /** VIP 用户：继承普通用户权限，额外拥有改进建议等付费功能 */
    VIP("vip", "VIP 用户"),

    /** 管理员：系统预设，可访问管理后台 */
    ADMIN("admin", "管理员"),
    ;

    /** 角色编码（用于 @SaCheckRole 注解） */
    private final String code;

    /** 角色描述 */
    private final String description;

    RoleEnum(String code, String description) {
        this.code = code;
        this.description = description;
    }
}
