CREATE TABLE `review_version` (
  `id` BIGINT NOT NULL, `version_code` VARCHAR(40) NOT NULL, `name` VARCHAR(100) NOT NULL,
  `description` VARCHAR(500) NOT NULL, `process_summary` VARCHAR(1000) NOT NULL,
  `final_contract_version` VARCHAR(20) NOT NULL, `status` VARCHAR(20) NOT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` TINYINT NOT NULL DEFAULT 0, PRIMARY KEY (`id`),
  UNIQUE INDEX `uk_version_code` (`version_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI评审版本定义';

INSERT INTO `review_version` (`id`, `version_code`, `name`, `description`, `process_summary`, `final_contract_version`, `status`)
VALUES (1, 'BASIC_REVIEW_V1', 'V1 基础 AI 评审', '一次多模态调用完成的基础论文评审',
        'PDF按页渲染为有序JPEG，与固定提示词一起提交DeepSeek Vision，一次返回最终JSON', 'SCORE_V1', 'ENABLED');

ALTER TABLE `review_task`
  ADD COLUMN `version_id` BIGINT NULL AFTER `submission_id`,
  ADD COLUMN `team_id` BIGINT NULL AFTER `version_id`,
  ADD COLUMN `problem_id` BIGINT NULL AFTER `team_id`,
  ADD COLUMN `prompt_snapshot` LONGTEXT NULL AFTER `workflow_version`,
  ADD COLUMN `attempt_no` INT NOT NULL DEFAULT 1 AFTER `retry_count`;
UPDATE `review_task` SET `version_id` = 1 WHERE `workflow_version` = 'BASIC_REVIEW_V1';
ALTER TABLE `review_task` MODIFY COLUMN `version_id` BIGINT NOT NULL;
UPDATE `review_task` t JOIN `review_result` r ON r.task_id = t.id
SET t.team_id = r.team_id, t.problem_id = r.problem_id;
ALTER TABLE `review_task` ADD INDEX `idx_team_create_time` (`team_id`, `create_time`);

RENAME TABLE `review_result` TO `review_v1_result`;
ALTER TABLE `review_v1_result`
  CHANGE COLUMN `total_score` `score` DECIMAL(5,2) NOT NULL,
  ADD COLUMN `model_name` VARCHAR(100) NULL AFTER `result_json`,
  ADD COLUMN `ai_call_id` VARCHAR(64) NULL AFTER `model_name`;

CREATE TABLE `review_task_log` (
  `id` BIGINT NOT NULL, `task_id` BIGINT NOT NULL, `workflow_version` VARCHAR(40) NOT NULL,
  `attempt_no` INT NOT NULL, `step_code` VARCHAR(40) NOT NULL, `step_name` VARCHAR(100) NOT NULL,
  `status` VARCHAR(20) NOT NULL, `started_at` DATETIME NOT NULL, `finished_at` DATETIME NULL,
  `duration_ms` BIGINT NULL, `input_summary` VARCHAR(500) NULL, `output_summary` VARCHAR(500) NULL,
  `ai_call_id` VARCHAR(64) NULL, `error_message` VARCHAR(500) NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` TINYINT NOT NULL DEFAULT 0, PRIMARY KEY (`id`),
  INDEX `idx_task_attempt` (`task_id`, `attempt_no`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI评审任务追加式步骤日志';
