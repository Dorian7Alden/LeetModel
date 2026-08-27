-- =============================================================
-- LeetModel Problem 服务 DDL — V1 初始化表结构
-- =============================================================

-- ----------------------------
-- 1. 题目主表
-- ----------------------------
DROP TABLE IF EXISTS `problem`;
CREATE TABLE `problem` (
  `id`              BIGINT NOT NULL COMMENT '题目ID（雪花算法）',
  `title`           VARCHAR(255) NOT NULL COMMENT '题目标题',
  `content_file_id` BIGINT DEFAULT NULL COMMENT '题目描述MD文件ID（关联oss_file）',
  `contest_type`    VARCHAR(20) NOT NULL COMMENT '赛事类型：MCM_ICM-美赛 CUMCM-国赛',
  `difficulty`      TINYINT NOT NULL DEFAULT 1 COMMENT '难度：1-简单 2-中等 3-困难',
  `average_score`   DECIMAL(5,2) UNSIGNED NOT NULL DEFAULT 0.00 COMMENT '平均得分',
  `status`          TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0-草稿 1-已发布 2-已下线 3-已归档',
  `creator_id`      BIGINT DEFAULT NULL COMMENT '创建者用户ID',
  `create_time`     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`         TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-正常 1-已删除',
  PRIMARY KEY (`id`),
  INDEX `idx_contest_difficulty` (`contest_type`, `difficulty`),
  INDEX `idx_status` (`status`),
  INDEX `idx_creator` (`creator_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='题目表';

-- ----------------------------
-- 2. 标签表
-- ----------------------------
DROP TABLE IF EXISTS `tag`;
CREATE TABLE `tag` (
  `id`          BIGINT NOT NULL COMMENT '标签ID（雪花算法）',
  `name`        VARCHAR(50) NOT NULL COMMENT '标签名称',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE INDEX `uk_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='标签表';

-- ----------------------------
-- 3. 题目-标签关联表
-- ----------------------------
DROP TABLE IF EXISTS `problem_tag`;
CREATE TABLE `problem_tag` (
  `id`          BIGINT NOT NULL COMMENT '关联ID（雪花算法）',
  `problem_id`  BIGINT NOT NULL COMMENT '题目ID',
  `tag_id`      BIGINT NOT NULL COMMENT '标签ID',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE INDEX `uk_problem_tag` (`problem_id`, `tag_id`),
  INDEX `idx_tag_id` (`tag_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='题目-标签关联表';

-- ----------------------------
-- 4. 题目外部链接表
-- ----------------------------
DROP TABLE IF EXISTS `problem_link`;
CREATE TABLE `problem_link` (
  `id`          BIGINT NOT NULL COMMENT '链接ID（雪花算法）',
  `problem_id`  BIGINT NOT NULL COMMENT '题目ID',
  `title`       VARCHAR(200) NOT NULL COMMENT '链接标题',
  `url`         VARCHAR(1024) NOT NULL COMMENT '链接地址',
  `description` VARCHAR(255) DEFAULT NULL COMMENT '链接说明',
  `sort_order`  INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '排序权重（升序）',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  INDEX `idx_problem_id` (`problem_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='题目外部链接表';
