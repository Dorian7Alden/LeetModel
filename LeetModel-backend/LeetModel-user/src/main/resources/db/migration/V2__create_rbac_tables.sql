-- ==================== RBAC 表 ====================
-- V2: 角色表 + 权限表 + 用户角色关联表 + 角色权限关联表 + 初始数据

-- 角色表
CREATE TABLE IF NOT EXISTS `role`
(
    `id`          BIGINT       NOT NULL COMMENT '主键',
    `code`        VARCHAR(32)  NOT NULL COMMENT '角色编码：admin / vip / user',
    `name`        VARCHAR(32)  NOT NULL COMMENT '角色名称',
    `description` VARCHAR(128) DEFAULT NULL COMMENT '角色描述',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_code` (`code`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='角色表';

-- 权限表
CREATE TABLE IF NOT EXISTS `permission`
(
    `id`          BIGINT       NOT NULL COMMENT '主键',
    `code`        VARCHAR(64)  NOT NULL COMMENT '权限编码：user:read, submission:create',
    `name`        VARCHAR(64)  NOT NULL COMMENT '权限名称',
    `description` VARCHAR(128) DEFAULT NULL COMMENT '权限描述',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_permission_code` (`code`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='权限表';

-- 用户-角色关联表
CREATE TABLE IF NOT EXISTS `user_role`
(
    `id`      BIGINT NOT NULL COMMENT '主键',
    `user_id` BIGINT NOT NULL COMMENT '用户 ID',
    `role_id` BIGINT NOT NULL COMMENT '角色 ID',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_role` (`user_id`, `role_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='用户角色关联';

-- 角色-权限关联表
CREATE TABLE IF NOT EXISTS `role_permission`
(
    `id`            BIGINT NOT NULL COMMENT '主键',
    `role_id`       BIGINT NOT NULL COMMENT '角色 ID',
    `permission_id` BIGINT NOT NULL COMMENT '权限 ID',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_permission` (`role_id`, `permission_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='角色权限关联';

-- ==================== 初始数据 ====================

-- 默认角色
INSERT INTO role (id, code, name, description, create_time, update_time)
VALUES (1, 'admin', '管理员', '系统预设最高权限', NOW(), NOW()),
       (2, 'vip', 'VIP用户', '可请求AI改进建议', NOW(), NOW()),
       (3, 'user', '普通用户', '注册默认角色', NOW(), NOW())
ON DUPLICATE KEY UPDATE name = VALUES(name);

-- 默认权限
INSERT INTO permission (id, code, name, create_time, update_time)
VALUES (1, 'user:read', '查看用户', NOW(), NOW()),
       (2, 'user:update', '修改用户', NOW(), NOW()),
       (3, 'submission:create', '提交作品', NOW(), NOW()),
       (4, 'suggestion:create', '请求改进建议', NOW(), NOW())
ON DUPLICATE KEY UPDATE name = VALUES(name);

-- role_permission 关联：admin 拥有全部权限
INSERT INTO role_permission (id, role_id, permission_id)
VALUES (1, 1, 1),
       (2, 1, 2),
       (3, 1, 3),
       (4, 1, 4)
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);

-- role_permission 关联：vip 拥有基础权限 + 改进建议
INSERT INTO role_permission (id, role_id, permission_id)
VALUES (5, 2, 3),
       (6, 2, 4)
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);

-- role_permission 关联：user 只有基础权限
INSERT INTO role_permission (id, role_id, permission_id)
VALUES (7, 3, 3)
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);
