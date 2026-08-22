-- ==================== 用户表 ====================
-- 依赖 common-core 的 BaseEntity 字段约定（id, create_time, update_time, deleted）
-- 本表在此基础上定义用户特有字段

CREATE TABLE IF NOT EXISTS `user`
(
    `id`          BIGINT       NOT NULL COMMENT '雪花算法主键',
    `username`    VARCHAR(32)  NOT NULL COMMENT '用户名，唯一',
    `password`    VARCHAR(128) NOT NULL COMMENT 'BCrypt 加密后的密码',
    `nickname`    VARCHAR(32)  DEFAULT NULL COMMENT '昵称',
    `email`       VARCHAR(64)  DEFAULT NULL COMMENT '邮箱',
    `avatar_url`  VARCHAR(256) DEFAULT NULL COMMENT '头像 URL',
    `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '账号状态：1=正常 0=禁用',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=正常 1=已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='用户表';
