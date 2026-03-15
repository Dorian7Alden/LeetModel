-- =============================================
-- LeetModel 数学建模实训平台 数据库建表SQL
-- 适配MySQL 5.7+/8.0+ 版本
-- 字符集：utf8mb4 支持emoji、特殊字符、公式
-- 引擎：InnoDB 支持事务、外键约束
-- =============================================

-- 数据库初始化
-- CREATE DATABASE IF NOT EXISTS leet_model 
-- DEFAULT CHARACTER SET utf8mb4 
-- COLLATE utf8mb4_unicode_ci;

-- USE leet_model;

-- 临时关闭外键检查，避免建表顺序导致的约束报错
SET FOREIGN_KEY_CHECKS = 0;

-- =============================================
-- 1. 用户表 user
-- 存储平台所有用户的基础信息、权限、训练角色
-- =============================================
CREATE TABLE `user` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '用户唯一ID',
  `username` VARCHAR(50) NOT NULL COMMENT '用户名/昵称',
  `email` VARCHAR(100) NOT NULL COMMENT '用户邮箱（登录唯一凭证）',
  `password` VARCHAR(255) NOT NULL COMMENT '用户密码（加密存储，推荐bcrypt哈希）',
  `school` VARCHAR(100) DEFAULT NULL COMMENT '所属学校',
  `phone` VARCHAR(20) DEFAULT NULL COMMENT '联系电话',
  `role` ENUM('tourist', 'user', 'admin') NOT NULL DEFAULT 'user' COMMENT '权限角色：tourist游客，user注册用户，admin平台管理员',
  `trainer_type` ENUM('modeler', 'coder', 'writer') DEFAULT NULL COMMENT '训练角色：modeler建模手，coder编程手，writer论文手',
  `status` ENUM('normal', 'disabled', 'banned') NOT NULL DEFAULT 'normal' COMMENT '账号状态：normal正常，disabled禁用，banned封禁',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '账号创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '信息更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_email` (`email`),
  UNIQUE KEY `uk_phone` (`phone`),
  KEY `idx_role` (`role`),
  KEY `idx_trainer_type` (`trainer_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户核心信息表';

-- =============================================
-- 2. 标签表 tag
-- 统一管理题目、赛事、帖子的全平台标签体系
-- =============================================
CREATE TABLE `tag` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '标签唯一ID',
  `name` VARCHAR(50) NOT NULL COMMENT '标签名称',
  `type` ENUM('model', 'competition', 'problem', 'background', 'post') NOT NULL COMMENT '标签类型：model模型类型，competition赛事类型，problem问题类型，background行业背景，post帖子类型',
  `sort` INT NOT NULL DEFAULT 0 COMMENT '标签排序权重，数值越小越靠前',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '标签创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '标签更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_name_type` (`name`, `type`),
  KEY `idx_type` (`type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='全平台标签统一管理表';

-- =============================================
-- 3. 题目表 problem
-- 存储数学建模题库，含往届真题、AI生成题、用户上传题
-- =============================================
CREATE TABLE `problem` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '题目唯一ID',
  `title` VARCHAR(255) NOT NULL COMMENT '题目标题',
  `description_content` LONGTEXT NOT NULL COMMENT '题目描述（富文本格式，支持图片、表格、公式）',
  `difficulty` ENUM('easy', 'medium', 'hard') NOT NULL DEFAULT 'medium' COMMENT '题目难度：easy简单，medium中等，hard困难',
  `source` ENUM('history_raw', 'ai_gen', 'user_upload') NOT NULL COMMENT '题目来源：history_raw往届赛事原题，ai_genAI生成模拟题，user_upload用户自主上传',
  `status` ENUM('unreviewed', 'rejected', 'pending', 'published', 'offline') NOT NULL DEFAULT 'unreviewed' COMMENT '题目状态：unreviewed未审核，rejected审核未通过，pending审核通过待上架，published已上架，offline已下架',
  `language` ENUM('CN', 'EN') NOT NULL DEFAULT 'CN' COMMENT '题目语言：CN中文，EN英文',
  `data_url` VARCHAR(500) DEFAULT NULL COMMENT '题目附件数据下载地址',
  `creator_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '题目创建者ID（用户上传时关联）',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '题目创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '题目更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_difficulty` (`difficulty`),
  KEY `idx_source` (`source`),
  KEY `idx_status` (`status`),
  KEY `idx_language` (`language`),
  KEY `idx_creator_id` (`creator_id`),
  CONSTRAINT `fk_problem_creator` FOREIGN KEY (`creator_id`) REFERENCES `user` (`id`) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数学建模题库核心表';

-- =============================================
-- 4. 题目-标签关联表 problem_tag
-- 题目与标签的多对多关联
-- =============================================
CREATE TABLE `problem_tag` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '关联记录ID',
  `problem_id` BIGINT UNSIGNED NOT NULL COMMENT '关联的题目ID',
  `tag_id` BIGINT UNSIGNED NOT NULL COMMENT '关联的标签ID',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '关联创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_problem_tag` (`problem_id`, `tag_id`),
  KEY `idx_tag_id` (`tag_id`),
  CONSTRAINT `fk_problem_tag_problem` FOREIGN KEY (`problem_id`) REFERENCES `problem` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_problem_tag_tag` FOREIGN KEY (`tag_id`) REFERENCES `tag` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='题目与标签多对多关联表';

-- =============================================
-- 5. 赛事表 competition
-- 存储国赛、美赛等主流数学建模赛事信息
-- =============================================
CREATE TABLE `competition` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '赛事唯一ID',
  `title` VARCHAR(255) NOT NULL COMMENT '赛事全称',
  `language` ENUM('CN', 'EN') NOT NULL DEFAULT 'CN' COMMENT '赛事语言：CN中文，EN英文',
  `introduction` TEXT NOT NULL COMMENT '赛事介绍',
  `sign_up_start_time` DATETIME DEFAULT NULL COMMENT '报名开始时间',
  `sign_up_end_time` DATETIME DEFAULT NULL COMMENT '报名结束时间',
  `start_time` DATETIME NOT NULL COMMENT '赛事开始时间',
  `end_time` DATETIME NOT NULL COMMENT '赛事结束时间',
  `official_url` VARCHAR(500) DEFAULT NULL COMMENT '赛事官网地址',
  `image_url` VARCHAR(500) DEFAULT NULL COMMENT '赛事封面图地址',
  `status` ENUM('upcoming', 'ongoing', 'finished') NOT NULL DEFAULT 'upcoming' COMMENT '赛事状态：upcoming未开始，ongoing进行中，finished已结束',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '赛事信息创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '赛事信息更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_status` (`status`),
  KEY `idx_start_time` (`start_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数学建模赛事信息表';

-- =============================================
-- 6. 赛事-标签关联表 competition_tag
-- 赛事与标签的多对多关联
-- =============================================
CREATE TABLE `competition_tag` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '关联记录ID',
  `competition_id` BIGINT UNSIGNED NOT NULL COMMENT '关联的赛事ID',
  `tag_id` BIGINT UNSIGNED NOT NULL COMMENT '关联的标签ID',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '关联创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_competition_tag` (`competition_id`, `tag_id`),
  KEY `idx_tag_id` (`tag_id`),
  CONSTRAINT `fk_competition_tag_competition` FOREIGN KEY (`competition_id`) REFERENCES `competition` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_competition_tag_tag` FOREIGN KEY (`tag_id`) REFERENCES `tag` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='赛事与标签多对多关联表';

-- =============================================
-- 7. 奖项表 prize
-- 存储赛事对应的奖项设置
-- =============================================
CREATE TABLE `prize` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '奖项唯一ID',
  `name` VARCHAR(100) NOT NULL COMMENT '奖项名称',
  `competition_id` BIGINT UNSIGNED NOT NULL COMMENT '所属赛事ID',
  `level` ENUM('special', 'first', 'second', 'third', 'excellent') NOT NULL COMMENT '奖项等级：special特等奖，first一等奖，second二等奖，third三等奖，excellent优秀奖',
  `description` TEXT DEFAULT NULL COMMENT '奖项说明、获奖要求',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '奖项创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '奖项更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_competition_id` (`competition_id`),
  CONSTRAINT `fk_prize_competition` FOREIGN KEY (`competition_id`) REFERENCES `competition` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='赛事奖项设置表';

-- =============================================
-- 8. 组队表 team
-- 管理用户组队全流程，完全适配需求中的组队规则
-- =============================================
CREATE TABLE `team` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '队伍唯一ID',
  `name` VARCHAR(100) NOT NULL COMMENT '队伍名称',
  `description` TEXT DEFAULT NULL COMMENT '队伍招募描述、能力要求',
  `competition_id` BIGINT UNSIGNED NOT NULL COMMENT '目标参赛赛事ID',
  `status` ENUM('recruiting', 'full', 'finished', 'destroyed') NOT NULL DEFAULT 'recruiting' COMMENT '队伍状态：recruiting组队中（人数未满），full满员待确认，finished组队成功，destroyed已解散',
  `school_limit` ENUM('inner', 'cross') NOT NULL DEFAULT 'cross' COMMENT '组队范围：inner仅校内，cross跨校',
  `school` VARCHAR(100) DEFAULT NULL COMMENT '发起人所属学校（校内组队筛选用）',
  `exam_status` ENUM('unstart', 'ongoing', 'finished') NOT NULL DEFAULT 'unstart' COMMENT '队伍考核状态：unstart未开始，ongoing进行中，finished已完成',
  `publisher_id` BIGINT UNSIGNED NOT NULL COMMENT '组队发起人ID',
  `leader_id` BIGINT UNSIGNED NOT NULL COMMENT '队长ID（默认发起人）',
  `member1_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '队员1ID',
  `member1_confirm` ENUM('unconfirm', 'confirmed', 'rejected') DEFAULT NULL COMMENT '队员1确认状态：unconfirm未确认，confirmed已确认，rejected已拒绝',
  `member2_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '队员2ID',
  `member2_confirm` ENUM('unconfirm', 'confirmed', 'rejected') DEFAULT NULL COMMENT '队员2确认状态：unconfirm未确认，confirmed已确认，rejected已拒绝',
  `max_member_num` TINYINT NOT NULL DEFAULT 3 COMMENT '队伍最大人数（默认3人，对应建模/编程/论文手）',
  `current_member_num` TINYINT NOT NULL DEFAULT 1 COMMENT '当前队伍人数（默认1人，发起人）',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '队伍创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '队伍信息更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_status` (`status`),
  KEY `idx_competition_id` (`competition_id`),
  KEY `idx_publisher_id` (`publisher_id`),
  KEY `idx_leader_id` (`leader_id`),
  CONSTRAINT `fk_team_competition` FOREIGN KEY (`competition_id`) REFERENCES `competition` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_team_publisher` FOREIGN KEY (`publisher_id`) REFERENCES `user` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_team_leader` FOREIGN KEY (`leader_id`) REFERENCES `user` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_team_member1` FOREIGN KEY (`member1_id`) REFERENCES `user` (`id`) ON DELETE SET NULL ON UPDATE CASCADE,
  CONSTRAINT `fk_team_member2` FOREIGN KEY (`member2_id`) REFERENCES `user` (`id`) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户组队全生命周期管理表';

-- =============================================
-- 9. 提交记录表 submission
-- 存储用户个人训练/赛事模拟的作品提交记录，仅支持个人提交
-- =============================================
CREATE TABLE `submission` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '提交记录唯一ID',
  `user_id` BIGINT UNSIGNED NOT NULL COMMENT '提交用户ID',
  `problem_id` BIGINT UNSIGNED NOT NULL COMMENT '关联的题目ID',
  `trainer_type` ENUM('modeler', 'coder', 'writer') NOT NULL COMMENT '提交对应的训练角色',
  `content` LONGTEXT DEFAULT NULL COMMENT '提交的文本内容（富文本/代码格式）',
  `file_url` VARCHAR(500) DEFAULT NULL COMMENT '提交的附件文件地址',
  `file_type` ENUM('md', 'latex', 'pdf', 'code', 'image') DEFAULT NULL COMMENT '文件类型：md markdown，latex，pdf，code代码，image图片',
  `file_md5` VARCHAR(32) DEFAULT NULL COMMENT '文件MD5值（防重复提交、文件校验）',
  `is_reviewed` TINYINT NOT NULL DEFAULT 0 COMMENT '是否已评审：0未评审，1已评审',
  `review_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '关联的评审结果ID',
  `status` ENUM('draft', 'submitted', 'reviewed') NOT NULL DEFAULT 'draft' COMMENT '提交状态：draft草稿，submitted已提交，reviewed已评审',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '提交记录创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '提交记录更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_problem_id` (`problem_id`),
  KEY `idx_is_reviewed` (`is_reviewed`),
  KEY `idx_status` (`status`),
  CONSTRAINT `fk_submission_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_submission_problem` FOREIGN KEY (`problem_id`) REFERENCES `problem` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户作品提交记录表（仅个人提交）';

-- =============================================
-- 10. 评审结果表 review
-- 存储AI/人工对作品的多维度加权评审结果、优化建议
-- =============================================
CREATE TABLE `review` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '评审结果唯一ID',
  `submission_id` BIGINT UNSIGNED NOT NULL COMMENT '关联的提交记录ID',
  `review_source` ENUM('ai', 'manual') NOT NULL DEFAULT 'ai' COMMENT '评审来源：ai AI智能评审，manual人工评审',
  `competition_standard` ENUM('CN', 'EN') NOT NULL DEFAULT 'CN' COMMENT '评审标准：CN国赛标准，EN美赛标准',
  `score1` DECIMAL(5,2) NOT NULL DEFAULT 0.00 COMMENT '维度1得分：建模合理性（建模手核心）',
  `weight1` DECIMAL(3,2) NOT NULL DEFAULT 0.20 COMMENT '维度1权重',
  `score2` DECIMAL(5,2) NOT NULL DEFAULT 0.00 COMMENT '维度2得分：代码准确性（编程手核心）',
  `weight2` DECIMAL(3,2) NOT NULL DEFAULT 0.20 COMMENT '维度2权重',
  `score3` DECIMAL(5,2) NOT NULL DEFAULT 0.00 COMMENT '维度3得分：论文规范性（论文手核心）',
  `weight3` DECIMAL(3,2) NOT NULL DEFAULT 0.20 COMMENT '维度3权重',
  `score4` DECIMAL(5,2) NOT NULL DEFAULT 0.00 COMMENT '维度4得分：图表呈现与可视化',
  `weight4` DECIMAL(3,2) NOT NULL DEFAULT 0.20 COMMENT '维度4权重',
  `score5` DECIMAL(5,2) NOT NULL DEFAULT 0.00 COMMENT '维度5得分：创新性与完整性',
  `weight5` DECIMAL(3,2) NOT NULL DEFAULT 0.20 COMMENT '维度5权重',
  `total_score` DECIMAL(5,2) NOT NULL DEFAULT 0.00 COMMENT '加权总分（满分100）',
  `review_content` TEXT NOT NULL COMMENT '完整评审报告',
  `optimize_advice` TEXT NOT NULL COMMENT '针对性优化建议',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '评审结果生成时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_submission_id` (`submission_id`),
  KEY `idx_total_score` (`total_score`),
  CONSTRAINT `fk_review_submission` FOREIGN KEY (`submission_id`) REFERENCES `submission` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='作品评审结果表，支持多维度加权打分';

-- =============================================
-- 11. 用户训练记录表 train_record_user
-- 记录用户个人全场景训练轨迹，仅个人训练，无组队训练
-- =============================================
CREATE TABLE `train_record_user` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '训练记录唯一ID',
  `user_id` BIGINT UNSIGNED NOT NULL COMMENT '训练用户ID',
  `problem_id` BIGINT UNSIGNED NOT NULL COMMENT '训练关联的题目ID',
  `trainer_type` ENUM('modeler', 'coder', 'writer') NOT NULL COMMENT '训练角色',
  `submission_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '关联的提交记录ID',
  `train_status` ENUM('ongoing', 'finished', 'abandoned') NOT NULL DEFAULT 'ongoing' COMMENT '训练状态：ongoing进行中，finished已完成，abandoned已放弃',
  `start_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '训练开始时间',
  `end_time` DATETIME DEFAULT NULL COMMENT '训练结束时间',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '记录更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_problem_role` (`user_id`, `problem_id`, `trainer_type`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_problem_id` (`problem_id`),
  KEY `idx_train_status` (`train_status`),
  CONSTRAINT `fk_train_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_train_problem` FOREIGN KEY (`problem_id`) REFERENCES `problem` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_train_submission` FOREIGN KEY (`submission_id`) REFERENCES `submission` (`id`) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户个人训练轨迹记录表';

-- =============================================
-- 12. 自定义测试表 custom_test
-- 存储平台/用户创建的分角色专项自定义测试题
-- =============================================
CREATE TABLE `custom_test` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '自定义测试唯一ID',
  `title` VARCHAR(255) NOT NULL COMMENT '测试标题',
  `content` LONGTEXT NOT NULL COMMENT '测试内容（富文本格式）',
  `test_type` ENUM('modeler', 'coder', 'writer') NOT NULL COMMENT '适配的训练角色',
  `creator_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '测试创建者ID（平台创建为NULL）',
  `difficulty` ENUM('easy', 'medium', 'hard') NOT NULL DEFAULT 'medium' COMMENT '测试难度',
  `status` ENUM('unreviewed', 'published', 'offline') NOT NULL DEFAULT 'published' COMMENT '测试状态',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '测试创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '测试更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_test_type` (`test_type`),
  KEY `idx_creator_id` (`creator_id`),
  CONSTRAINT `fk_custom_test_creator` FOREIGN KEY (`creator_id`) REFERENCES `user` (`id`) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='分角色自定义专项测试表';

-- =============================================
-- 13. 社区帖子表 post
-- 存储用户发布的备赛经验、讨论、技巧分享内容
-- =============================================
CREATE TABLE `post` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '帖子唯一ID',
  `publisher_id` BIGINT UNSIGNED NOT NULL COMMENT '发布者用户ID',
  `type` ENUM('experience', 'discuss', 'skill') NOT NULL COMMENT '帖子类型：experience经验分享，discuss问题讨论，skill技巧干货',
  `title` VARCHAR(255) NOT NULL COMMENT '帖子标题',
  `content` LONGTEXT NOT NULL COMMENT '帖子内容（富文本格式）',
  `like_cnt` INT NOT NULL DEFAULT 0 COMMENT '点赞数',
  `comment_cnt` INT NOT NULL DEFAULT 0 COMMENT '评论数',
  `view_cnt` INT NOT NULL DEFAULT 0 COMMENT '浏览数',
  `heat` INT NOT NULL DEFAULT 0 COMMENT '帖子热度值（用于排序）',
  `is_top` TINYINT NOT NULL DEFAULT 0 COMMENT '是否置顶：0否，1是',
  `status` ENUM('unreviewed', 'published', 'rejected') NOT NULL DEFAULT 'unreviewed' COMMENT '帖子状态：unreviewed未审核，published已发布，rejected审核未通过',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '帖子发布时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '帖子更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_publisher_id` (`publisher_id`),
  KEY `idx_type` (`type`),
  KEY `idx_status` (`status`),
  KEY `idx_heat` (`heat`),
  CONSTRAINT `fk_post_publisher` FOREIGN KEY (`publisher_id`) REFERENCES `user` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='社区帖子核心表';

-- =============================================
-- 14. 帖子-标签关联表 post_tag
-- 帖子与标签的多对多关联
-- =============================================
CREATE TABLE `post_tag` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '关联记录ID',
  `post_id` BIGINT UNSIGNED NOT NULL COMMENT '关联的帖子ID',
  `tag_id` BIGINT UNSIGNED NOT NULL COMMENT '关联的标签ID',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '关联创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_post_tag` (`post_id`, `tag_id`),
  KEY `idx_tag_id` (`tag_id`),
  CONSTRAINT `fk_post_tag_post` FOREIGN KEY (`post_id`) REFERENCES `post` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_post_tag_tag` FOREIGN KEY (`tag_id`) REFERENCES `tag` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='帖子与标签多对多关联表';

-- =============================================
-- 15. 评论表 comment
-- 存储帖子的评论、二级回复，支持楼中楼
-- =============================================
CREATE TABLE `comment` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '评论唯一ID',
  `post_id` BIGINT UNSIGNED NOT NULL COMMENT '关联的帖子ID',
  `user_id` BIGINT UNSIGNED NOT NULL COMMENT '评论发布者用户ID',
  `parent_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '父评论ID（NULL为一级评论，非NULL为二级回复）',
  `content` TEXT NOT NULL COMMENT '评论内容',
  `like_cnt` INT NOT NULL DEFAULT 0 COMMENT '点赞数',
  `status` ENUM('normal', 'deleted', 'rejected') NOT NULL DEFAULT 'normal' COMMENT '评论状态：normal正常，deleted已删除，rejected审核驳回',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '评论发布时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '评论更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_post_id` (`post_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_parent_id` (`parent_id`),
  KEY `idx_status` (`status`),
  CONSTRAINT `fk_comment_post` FOREIGN KEY (`post_id`) REFERENCES `post` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_comment_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_comment_parent` FOREIGN KEY (`parent_id`) REFERENCES `comment` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='帖子评论表，支持二级回复';

-- 重新开启外键检查
SET FOREIGN_KEY_CHECKS = 1;