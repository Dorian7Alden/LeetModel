SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for submission
-- ----------------------------
DROP TABLE IF EXISTS `submission`;
CREATE TABLE `submission` (
  `submission_id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '作品提交主键ID',
  `problem_id` bigint(20) NOT NULL COMMENT '关联题目ID',
  `user_id` bigint(20) NOT NULL COMMENT '提交者用户ID',
  `title` varchar(255) NOT NULL COMMENT '作品标题',
  `content_file_id` bigint(20) DEFAULT NULL COMMENT '提交的md文件ID',
  `status` varchar(20) NOT NULL DEFAULT 'PENDING' COMMENT '状态: PENDING/EVALUATING/COMPLETED/FAILED',
  `total_score` decimal(5,2) DEFAULT NULL COMMENT '加权总分',
  `submit_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '提交时间',
  `complete_time` datetime DEFAULT NULL COMMENT '评审完成时间',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`submission_id`),
  INDEX `idx_problem_id` (`problem_id`),
  INDEX `idx_user_id` (`user_id`),
  INDEX `idx_status` (`status`),
  CONSTRAINT `fk_submission_problem` FOREIGN KEY (`problem_id`) REFERENCES `problem` (`problem_id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_submission_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`user_id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_submission_content_file` FOREIGN KEY (`content_file_id`) REFERENCES `oss_file` (`file_id`) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='作品提交表';

-- ----------------------------
-- Table structure for review
-- ----------------------------
DROP TABLE IF EXISTS `review`;
CREATE TABLE `review` (
  `review_id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '评审结果主键ID',
  `submission_id` bigint(20) NOT NULL COMMENT '关联作品ID',
  `dimension_code` varchar(50) NOT NULL COMMENT '评审维度编码',
  `dimension_name` varchar(100) NOT NULL COMMENT '评审维度名称',
  `score` decimal(5,2) DEFAULT NULL COMMENT '该维度得分(0-100)',
  `weight` decimal(3,2) NOT NULL COMMENT '维度权重',
  `feedback` text COMMENT 'AI评审反馈文本',
  `status` varchar(20) NOT NULL DEFAULT 'PENDING' COMMENT '状态: PENDING/RUNNING/COMPLETED/FAILED',
  `retry_count` int NOT NULL DEFAULT 0 COMMENT '重试次数',
  `start_time` datetime DEFAULT NULL COMMENT '评审开始时间',
  `end_time` datetime DEFAULT NULL COMMENT '评审结束时间',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`review_id`),
  UNIQUE INDEX `uk_submission_dimension` (`submission_id`, `dimension_code`),
  INDEX `idx_submission_id` (`submission_id`),
  INDEX `idx_status` (`status`),
  CONSTRAINT `fk_review_submission` FOREIGN KEY (`submission_id`) REFERENCES `submission` (`submission_id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='维度评审结果表';

-- ----------------------------
-- Table structure for review_log
-- ----------------------------
DROP TABLE IF EXISTS `review_log`;
CREATE TABLE `review_log` (
  `log_id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '评审日志主键ID',
  `submission_id` bigint(20) NOT NULL COMMENT '关联作品ID',
  `review_id` bigint(20) DEFAULT NULL COMMENT '关联评审记录ID',
  `status` varchar(10) NOT NULL COMMENT '状态: SUCCESS/FAIL',
  `message` varchar(500) DEFAULT NULL COMMENT '日志简要信息',
  `detail` longtext COMMENT '详细信息(AI请求prompt、原始响应、异常信息)',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`log_id`),
  INDEX `idx_submission_id` (`submission_id`),
  INDEX `idx_review_id` (`review_id`),
  CONSTRAINT `fk_review_log_submission` FOREIGN KEY (`submission_id`) REFERENCES `submission` (`submission_id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_review_log_review` FOREIGN KEY (`review_id`) REFERENCES `review` (`review_id`) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='评审日志表';

SET FOREIGN_KEY_CHECKS = 1;
