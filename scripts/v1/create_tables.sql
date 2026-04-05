-- ================================================================
-- LeetModel 标签系统数据库表创建脚本
-- ================================================================
-- 创建时间: 2026-03-28
-- 说明: 包含标签分类表、标签表、题目-标签关联表的完整定义
-- ================================================================


-- ================================================================
-- 标签分类表 tag_category
-- ================================================================
CREATE TABLE IF NOT EXISTS `tag_category` (
    `id`            BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '分类ID',
    `name`          VARCHAR(50)     NOT NULL                 COMMENT '分类名称',
    `code`          VARCHAR(50)     NOT NULL                 COMMENT '分类编码（唯一标识）',
    `icon`          VARCHAR(100)    DEFAULT NULL             COMMENT '分类图标（URL格式）',
    `description`   VARCHAR(255)    DEFAULT NULL             COMMENT '分类描述',
    `sort_order`    INT             NOT NULL DEFAULT 0       COMMENT '排序权重（升序）',
    `is_multiple`   TINYINT(1)      NOT NULL DEFAULT 1       COMMENT '是否允许多选：0-单选 1-多选',
    `is_required`   TINYINT(1)      NOT NULL DEFAULT 0       COMMENT '是否必选：0-可选 1-必选',
    `status`        TINYINT(1)      NOT NULL DEFAULT 1       COMMENT '状态：0-禁用 1-启用',
    `created_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP  COMMENT '创建时间',
    `updated_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='标签分类表';


-- ================================================================
-- 标签表 tag
-- ================================================================
CREATE TABLE IF NOT EXISTS `tag` (
    `id`            BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '标签ID',
    `category_id`   BIGINT          NOT NULL                 COMMENT '所属分类ID',
    `parent_id`     BIGINT          NOT NULL DEFAULT 0       COMMENT '父标签ID（0=顶级标签）',
    `name`          VARCHAR(50)     NOT NULL                 COMMENT '标签名称',
    `code`          VARCHAR(50)     NOT NULL                 COMMENT '标签编码（全局唯一）',
    `color`         VARCHAR(20)     DEFAULT NULL             COMMENT '标签颜色（Hex色值）',
    `icon`          VARCHAR(100)    DEFAULT NULL             COMMENT '标签图标（URL格式）',
    `description`   VARCHAR(255)    DEFAULT NULL             COMMENT '标签描述',
    `sort_order`    INT             NOT NULL DEFAULT 0       COMMENT '排序权重（升序）',
    `usage_count`   INT             NOT NULL DEFAULT 0       COMMENT '引用次数（冗余计数）',
    `status`        TINYINT(1)      NOT NULL DEFAULT 1       COMMENT '状态：0-禁用 1-启用',
    `created_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP  COMMENT '创建时间',
    `updated_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_code` (`code`),
    KEY `idx_category_id` (`category_id`),
    KEY `idx_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='标签表';


-- ================================================================
-- 题目-标签关联表 problem_tag
-- ================================================================
CREATE TABLE IF NOT EXISTS `problem_tag` (
    `id`            BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '主键ID',
    `problem_id`    BIGINT          NOT NULL                 COMMENT '题目ID',
    `tag_id`        BIGINT          NOT NULL                 COMMENT '标签ID',
    `created_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP  COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_problem_tag` (`problem_id`, `tag_id`),
    KEY `idx_tag_id` (`tag_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='题目-标签关联表';
