package com.leetmodel.user.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.leetmodel.common.core.bean.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户实体。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("user")
public class User extends BaseEntity {

    /** 用户名，唯一 */
    private String username;

    /** BCrypt 加密后的密码 */
    private String password;

    /** 昵称 */
    private String nickname;

    /** 邮箱 */
    private String email;

    /** 当前头像文件资产 ID，物理对象与访问策略由 file-service 管理 */
    private Long avatarFileId;

    /** 外部头像绝对 URL，仅用于演示账号与遗留数据 */
    private String avatarUrl;

    /** 账号状态：1=正常 0=禁用 */
    private Integer status;
}
