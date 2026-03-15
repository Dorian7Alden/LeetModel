-- =============================================
-- LeetModel 测试版建表SQL（无任何约束）
-- 仅保留基础表结构与字段类型
-- =============================================

-- CREATE DATABASE IF NOT EXISTS leet_model_test 
-- DEFAULT CHARACTER SET utf8mb4;

-- USE leet_model_test;

-- 1. 用户表
CREATE TABLE `user` (
  `id` BIGINT,
  `username` VARCHAR(50),
  `email` VARCHAR(100),
  `password` VARCHAR(255),
  `school` VARCHAR(100),
  `phone` VARCHAR(20),
  `role` VARCHAR(20),
  `trainer_type` VARCHAR(20),
  `status` VARCHAR(20),
  `create_time` DATETIME,
  `update_time` DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 2. 标签表
CREATE TABLE `tag` (
  `id` BIGINT,
  `name` VARCHAR(50),
  `type` VARCHAR(20),
  `sort` INT,
  `create_time` DATETIME,
  `update_time` DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 3. 题目表
CREATE TABLE `problem` (
  `id` BIGINT,
  `title` VARCHAR(255),
  `description_content` LONGTEXT,
  `difficulty` VARCHAR(20),
  `source` VARCHAR(20),
  `status` VARCHAR(20),
  `language` VARCHAR(10),
  `data_url` VARCHAR(500),
  `creator_id` BIGINT,
  `create_time` DATETIME,
  `update_time` DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 4. 题目-标签关联表
CREATE TABLE `problem_tag` (
  `id` BIGINT,
  `problem_id` BIGINT,
  `tag_id` BIGINT,
  `create_time` DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 5. 赛事表
CREATE TABLE `competition` (
  `id` BIGINT,
  `title` VARCHAR(255),
  `language` VARCHAR(10),
  `introduction` TEXT,
  `sign_up_start_time` DATETIME,
  `sign_up_end_time` DATETIME,
  `start_time` DATETIME,
  `end_time` DATETIME,
  `official_url` VARCHAR(500),
  `image_url` VARCHAR(500),
  `status` VARCHAR(20),
  `create_time` DATETIME,
  `update_time` DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 6. 赛事-标签关联表
CREATE TABLE `competition_tag` (
  `id` BIGINT,
  `competition_id` BIGINT,
  `tag_id` BIGINT,
  `create_time` DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 7. 奖项表
CREATE TABLE `prize` (
  `id` BIGINT,
  `name` VARCHAR(100),
  `competition_id` BIGINT,
  `level` VARCHAR(20),
  `description` TEXT,
  `create_time` DATETIME,
  `update_time` DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 8. 组队表
CREATE TABLE `team` (
  `id` BIGINT,
  `name` VARCHAR(100),
  `description` TEXT,
  `competition_id` BIGINT,
  `status` VARCHAR(20),
  `school_limit` VARCHAR(20),
  `school` VARCHAR(100),
  `exam_status` VARCHAR(20),
  `publisher_id` BIGINT,
  `leader_id` BIGINT,
  `member1_id` BIGINT,
  `member1_confirm` VARCHAR(20),
  `member2_id` BIGINT,
  `member2_confirm` VARCHAR(20),
  `max_member_num` TINYINT,
  `current_member_num` TINYINT,
  `create_time` DATETIME,
  `update_time` DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 9. 提交记录表
CREATE TABLE `submission` (
  `id` BIGINT,
  `user_id` BIGINT,
  `problem_id` BIGINT,
  `trainer_type` VARCHAR(20),
  `content` LONGTEXT,
  `file_url` VARCHAR(500),
  `file_type` VARCHAR(20),
  `file_md5` VARCHAR(32),
  `is_reviewed` TINYINT,
  `review_id` BIGINT,
  `status` VARCHAR(20),
  `create_time` DATETIME,
  `update_time` DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 10. 评审结果表
CREATE TABLE `review` (
  `id` BIGINT,
  `submission_id` BIGINT,
  `review_source` VARCHAR(20),
  `competition_standard` VARCHAR(10),
  `score1` DECIMAL(5,2),
  `weight1` DECIMAL(3,2),
  `score2` DECIMAL(5,2),
  `weight2` DECIMAL(3,2),
  `score3` DECIMAL(5,2),
  `weight3` DECIMAL(3,2),
  `score4` DECIMAL(5,2),
  `weight4` DECIMAL(3,2),
  `score5` DECIMAL(5,2),
  `weight5` DECIMAL(3,2),
  `total_score` DECIMAL(5,2),
  `review_content` TEXT,
  `optimize_advice` TEXT,
  `create_time` DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 11. 用户训练记录表
CREATE TABLE `train_record_user` (
  `id` BIGINT,
  `user_id` BIGINT,
  `problem_id` BIGINT,
  `trainer_type` VARCHAR(20),
  `submission_id` BIGINT,
  `train_status` VARCHAR(20),
  `start_time` DATETIME,
  `end_time` DATETIME,
  `create_time` DATETIME,
  `update_time` DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 12. 自定义测试表
CREATE TABLE `custom_test` (
  `id` BIGINT,
  `title` VARCHAR(255),
  `content` LONGTEXT,
  `test_type` VARCHAR(20),
  `creator_id` BIGINT,
  `difficulty` VARCHAR(20),
  `status` VARCHAR(20),
  `create_time` DATETIME,
  `update_time` DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 13. 社区帖子表
CREATE TABLE `post` (
  `id` BIGINT,
  `publisher_id` BIGINT,
  `type` VARCHAR(20),
  `title` VARCHAR(255),
  `content` LONGTEXT,
  `like_cnt` INT,
  `comment_cnt` INT,
  `view_cnt` INT,
  `heat` INT,
  `is_top` TINYINT,
  `status` VARCHAR(20),
  `create_time` DATETIME,
  `update_time` DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 14. 帖子-标签关联表
CREATE TABLE `post_tag` (
  `id` BIGINT,
  `post_id` BIGINT,
  `tag_id` BIGINT,
  `create_time` DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 15. 评论表
CREATE TABLE `comment` (
  `id` BIGINT,
  `post_id` BIGINT,
  `user_id` BIGINT,
  `parent_id` BIGINT,
  `content` TEXT,
  `like_cnt` INT,
  `status` VARCHAR(20),
  `create_time` DATETIME,
  `update_time` DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;