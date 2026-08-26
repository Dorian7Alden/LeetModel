CREATE TABLE `review_task` (
  `id` BIGINT NOT NULL, `submission_id` BIGINT NOT NULL, `status` VARCHAR(20) NOT NULL,
  `workflow_version` VARCHAR(40) NOT NULL, `retry_count` INT NOT NULL DEFAULT 0,
  `next_run_at` DATETIME NOT NULL, `started_at` DATETIME NULL, `finished_at` DATETIME NULL,
  `error_message` VARCHAR(500) NULL, `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` TINYINT NOT NULL DEFAULT 0, PRIMARY KEY (`id`),
  UNIQUE INDEX `uk_submission_workflow` (`submission_id`, `workflow_version`),
  INDEX `idx_status_next_run_at` (`status`, `next_run_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='持久化评审任务';

CREATE TABLE `review_result` (
  `id` BIGINT NOT NULL, `task_id` BIGINT NOT NULL, `submission_id` BIGINT NOT NULL,
  `team_id` BIGINT NOT NULL, `problem_id` BIGINT NOT NULL, `workflow_version` VARCHAR(40) NOT NULL,
  `total_score` DECIMAL(5,2) NOT NULL, `result_json` JSON NOT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` TINYINT NOT NULL DEFAULT 0, PRIMARY KEY (`id`), UNIQUE INDEX `uk_task_id` (`task_id`),
  INDEX `idx_team_create_time` (`team_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评审结果';
