-- =============================================================
-- LeetModel Team 服务 DDL — V1 初始化表结构
-- =============================================================

-- 1. 团队表
CREATE TABLE IF NOT EXISTS `team`
(
    `id`          BIGINT       NOT NULL COMMENT '团队ID（雪花算法）',
    `name`        VARCHAR(64)  NOT NULL COMMENT '团队名称',
    `description` VARCHAR(256) DEFAULT NULL COMMENT '团队描述',
    `leader_id`   BIGINT       NOT NULL COMMENT '队长用户ID',
    `max_members` INT          NOT NULL DEFAULT 3 COMMENT '最大成员数',
    `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1=活跃 0=已解散',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=正常 1=已删除',
    PRIMARY KEY (`id`),
    INDEX `idx_leader` (`leader_id`),
    INDEX `idx_status` (`status`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='团队表';

-- 2. 团队成员表
CREATE TABLE IF NOT EXISTS `team_member`
(
    `id`          BIGINT   NOT NULL COMMENT '主键（雪花算法）',
    `team_id`     BIGINT   NOT NULL COMMENT '团队ID',
    `user_id`     BIGINT   NOT NULL COMMENT '用户ID',
    `role`        VARCHAR(16) NOT NULL DEFAULT 'member' COMMENT '成员角色：leader / member',
    `joined_at`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '加入时间',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_team` (`user_id`, `team_id`),
    INDEX `idx_team_id` (`team_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='团队成员表';
