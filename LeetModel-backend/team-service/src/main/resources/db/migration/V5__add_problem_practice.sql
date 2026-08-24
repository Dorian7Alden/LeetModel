ALTER TABLE `team`
  ADD COLUMN `problem_id` BIGINT NULL COMMENT '练习题目ID' AFTER `leader_id`,
  ADD COLUMN `practice_status` VARCHAR(20) NOT NULL DEFAULT 'PREPARING' COMMENT '练习状态' AFTER `status`,
  ADD COLUMN `started_at` DATETIME NULL COMMENT '开始时间' AFTER `practice_status`,
  ADD COLUMN `deadline_at` DATETIME NULL COMMENT '截止时间' AFTER `started_at`,
  ADD COLUMN `ended_at` DATETIME NULL COMMENT '结束时间' AFTER `deadline_at`,
  ADD INDEX `idx_problem_practice_status` (`problem_id`, `practice_status`);

UPDATE `team` SET `problem_id` = 0 WHERE `problem_id` IS NULL;
ALTER TABLE `team` MODIFY COLUMN `problem_id` BIGINT NOT NULL;
