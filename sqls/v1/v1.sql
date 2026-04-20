/*
 Navicat Premium Dump SQL

 Source Server         : 47.111.180.134
 Source Server Type    : MySQL
 Source Server Version : 50744 (5.7.44)
 Source Host           : 47.111.180.134:3306
 Source Schema         : leet-model

 Target Server Type    : MySQL
 Target Server Version : 50744 (5.7.44)
 File Encoding         : 65001

 Date: 21/04/2026 01:46:11
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

ALTER DATABASE `leet-model` CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci;


-- ----------------------------
-- Table structure for oss_file
-- ----------------------------
DROP TABLE IF EXISTS `oss_file`;
CREATE TABLE `oss_file`  (
  `file_id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'OSS文件表主键ID',
  `file_name` varchar(255) NOT NULL COMMENT 'OSS文件原始名称',
  `file_url` varchar(1024) NOT NULL COMMENT 'OSS文件访问URL地址',
  `file_suffix` varchar(50) NULL DEFAULT NULL COMMENT 'OSS文件原始后缀名',
  `content_type` varchar(100) NULL DEFAULT NULL COMMENT 'OSS文件MIME类型（如image/png）',
  `file_size` bigint(20) NULL DEFAULT NULL COMMENT '文件大小（字节）',
  `uploader_id` bigint(20) NULL DEFAULT NULL COMMENT 'OSS文件上传用户ID（关联user.user_id）',
  `is_deleted` tinyint(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除 1-已删除',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'OSS文件记录创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'OSS文件记录更新时间',
  PRIMARY KEY (`file_id`) USING BTREE,
  INDEX `idx_uploader_deleted`(`uploader_id`, `is_deleted`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = 'OSS文件表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for user
-- ----------------------------
DROP TABLE IF EXISTS `user`;
CREATE TABLE `user`  (
  `user_id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '用户表主键ID',
  `username` varchar(50) NOT NULL COMMENT '用户登录名/昵称',
  `email` varchar(100) NOT NULL COMMENT '用户邮箱地址',
  `password` varchar(255) NOT NULL COMMENT '用户密码哈希值（加密存储）',
  `school` varchar(100) NULL DEFAULT NULL COMMENT '用户所属学校',
  `phone` varchar(20) NULL DEFAULT NULL COMMENT '用户手机号',
  `avatar_file_id` bigint(20) NULL DEFAULT NULL COMMENT '用户头像文件ID（关联oss_file.file_id）',
  `status` enum('active','inactive','banned','deleted') NOT NULL DEFAULT 'active' COMMENT '用户账号状态：active-正常 inactive-未激活 banned-封禁 deleted-已注销',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '用户记录创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '用户记录更新时间',
  PRIMARY KEY (`user_id`) USING BTREE,
  UNIQUE INDEX `uk_username`(`username`) USING BTREE,
  UNIQUE INDEX `uk_email`(`email`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE,
  INDEX `idx_avatar_file_id`(`avatar_file_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1001 DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '用户表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for tag_category
-- ----------------------------
DROP TABLE IF EXISTS `tag_category`;
CREATE TABLE `tag_category`  (
  `category_id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '标签分类表主键ID',
  `name` varchar(50) NOT NULL COMMENT '标签分类名称',
  `code` varchar(50) NOT NULL COMMENT '标签分类编码（唯一标识）',
  `icon_file_id` bigint(20) NULL DEFAULT NULL COMMENT '标签分类图标文件ID（关联oss_file.file_id）',
  `description` varchar(255) NULL DEFAULT NULL COMMENT '标签分类描述说明',
  `sort_order` int(11) NOT NULL DEFAULT 0 COMMENT '排序权重（升序）',
  `is_multiple` tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否允许多选：0-单选 1-多选',
  `is_required` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否必选：0-可选 1-必选',
  `status` tinyint(1) NOT NULL DEFAULT 1 COMMENT '状态：0-禁用 1-启用',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '标签分类记录创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '标签分类记录更新时间',
  PRIMARY KEY (`category_id`) USING BTREE,
  UNIQUE INDEX `uk_code`(`code`) USING BTREE,
  INDEX `idx_sort_status`(`status`, `sort_order`) USING BTREE,
  INDEX `idx_icon_file_id`(`icon_file_id`) USING BTREE,
  CONSTRAINT `fk_tag_category_icon_file` FOREIGN KEY (`icon_file_id`) REFERENCES `oss_file` (`file_id`) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE = InnoDB AUTO_INCREMENT = 9 DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '标签分类表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for problem
-- ----------------------------
DROP TABLE IF EXISTS `problem`;
CREATE TABLE `problem`  (
  `problem_id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '题目表主键ID',
  `problem_title` varchar(255) NOT NULL COMMENT '题目名称（标题）',
  `content_file_id` bigint(20) NULL DEFAULT NULL COMMENT '题目内容文件ID（关联oss_file.file_id）',
  `ave_score` decimal(5, 2) UNSIGNED NOT NULL DEFAULT 0.00 COMMENT '题目平均得分（支持小数）',
  `problem_status` enum('draft','published','offline','archived') NOT NULL DEFAULT 'draft' COMMENT '题目状态：draft-草稿 published-已发布 offline-已下线 archived-已归档',
  `creator_id` bigint(20) NULL DEFAULT NULL COMMENT '题目创建用户ID（关联user.user_id）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '题目记录创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '题目记录更新时间',
  PRIMARY KEY (`problem_id`) USING BTREE,
  INDEX `idx_content_file_id`(`content_file_id`) USING BTREE,
  CONSTRAINT `fk_problem_creator` FOREIGN KEY (`creator_id`) REFERENCES `user` (`user_id`) ON DELETE SET NULL ON UPDATE CASCADE,
  CONSTRAINT `fk_problem_content_file` FOREIGN KEY (`content_file_id`) REFERENCES `oss_file` (`file_id`) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE = InnoDB AUTO_INCREMENT = 1 DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '题目表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for link
-- ----------------------------
DROP TABLE IF EXISTS `link`;
CREATE TABLE `link`  (
  `link_id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '链接表主键ID',
  `link_title` varchar(200) NOT NULL COMMENT '链接标题',
  `link_url` varchar(1024) NOT NULL COMMENT '链接地址',
  `description` varchar(255) NULL DEFAULT NULL COMMENT '链接说明',
  `status` tinyint(1) NOT NULL DEFAULT 1 COMMENT '状态：0-禁用 1-启用',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '链接记录创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '链接记录更新时间',
  PRIMARY KEY (`link_id`) USING BTREE,
  INDEX `idx_status`(`status`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '链接表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for problem_link
-- ----------------------------
DROP TABLE IF EXISTS `problem_link`;
CREATE TABLE `problem_link`  (
  `problem_link_id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '题目-链接关联表主键ID',
  `problem_id` bigint(20) NOT NULL COMMENT '题目ID（关联problem.problem_id）',
  `link_id` bigint(20) NOT NULL COMMENT '链接ID（关联link.link_id）',
  `sort_order` int(11) NOT NULL DEFAULT 0 COMMENT '链接展示优先级',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '题目-链接关联记录创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '题目-链接关联记录更新时间',
  `status` tinyint(1) NOT NULL DEFAULT 1 COMMENT '状态：0-禁用 1-启用',
  PRIMARY KEY (`problem_link_id`) USING BTREE,
  UNIQUE INDEX `uk_problem_link`(`problem_id`, `link_id`) USING BTREE,
  INDEX `idx_problem_id`(`problem_id`) USING BTREE,
  INDEX `idx_link_id`(`link_id`) USING BTREE,
  CONSTRAINT `fk_problem_link_problem` FOREIGN KEY (`problem_id`) REFERENCES `problem` (`problem_id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_problem_link_link` FOREIGN KEY (`link_id`) REFERENCES `link` (`link_id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE = InnoDB AUTO_INCREMENT = 1 DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '题目-链接关联表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for tag
-- ----------------------------
DROP TABLE IF EXISTS `tag`;
CREATE TABLE `tag`  (
  `tag_id` bigint(20) NOT NULL COMMENT '标签表主键ID（非自增，业务侧维护）',
  `category_id` bigint(20) NOT NULL COMMENT '标签所属分类ID（关联tag_category.category_id）',
  `name` varchar(50) NOT NULL COMMENT '标签显示名称',
  `description` varchar(255) NULL DEFAULT NULL COMMENT '标签说明描述',
  `sort_order` int(11) NOT NULL DEFAULT 0 COMMENT '排序权重（升序）',
  `usage_count` int(11) NOT NULL DEFAULT 0 COMMENT '引用次数（冗余计数）',
  `status` tinyint(1) NOT NULL DEFAULT 1 COMMENT '状态：0-禁用 1-启用',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '标签记录创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '标签记录更新时间',
  PRIMARY KEY (`tag_id`) USING BTREE,
  UNIQUE INDEX `uk_category_name`(`category_id`, `name`) USING BTREE,
  CONSTRAINT `fk_tag_category` FOREIGN KEY (`category_id`) REFERENCES `tag_category` (`category_id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '标签表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for problem_tag
-- ----------------------------
DROP TABLE IF EXISTS `problem_tag`;
CREATE TABLE `problem_tag`  (
  `problem_tag_id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '题目标签关联表主键ID',
  `problem_id` bigint(20) NOT NULL COMMENT '题目标签关联表题目ID（关联problem.problem_id）',
  `tag_id` bigint(20) NOT NULL COMMENT '题目标签关联表标签ID（关联tag.tag_id）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '题目标签关联记录创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '题目标签关联记录更新时间',
  PRIMARY KEY (`problem_tag_id`) USING BTREE,
  UNIQUE INDEX `uk_problem_tag`(`problem_id`, `tag_id`) USING BTREE,
  INDEX `idx_tag_id`(`tag_id`) USING BTREE,
  CONSTRAINT `fk_problem_tag_problem` FOREIGN KEY (`problem_id`) REFERENCES `problem` (`problem_id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_problem_tag_tag` FOREIGN KEY (`tag_id`) REFERENCES `tag` (`tag_id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE = InnoDB AUTO_INCREMENT = 35 DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '题目-标签关联表' ROW_FORMAT = Dynamic;

SET FOREIGN_KEY_CHECKS = 1;
