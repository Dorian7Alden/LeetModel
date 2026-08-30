ALTER TABLE `evaluation_task`
  ADD COLUMN `weight_scheme_id` BIGINT NULL AFTER `metric_set_version`,
  ADD COLUMN `weight_scheme_version` VARCHAR(64) NULL AFTER `weight_scheme_id`,
  ADD COLUMN `weight_scheme_snapshot_json` LONGTEXT NULL AFTER `weight_scheme_version`,
  ADD INDEX `idx_weight_scheme_create_time` (`weight_scheme_id`, `create_time`);

CREATE TABLE `evaluation_score_result` (
  `id` BIGINT NOT NULL,
  `task_id` BIGINT NOT NULL,
  `score_result_version` VARCHAR(64) NOT NULL,
  `weight_scheme_id` BIGINT NOT NULL,
  `weight_scheme_version` VARCHAR(64) NOT NULL,
  `metric_set_version` VARCHAR(64) NOT NULL,
  `weight_scheme_snapshot_json` LONGTEXT NOT NULL,
  `raw_metrics_snapshot_json` LONGTEXT NOT NULL,
  `status` VARCHAR(20) NOT NULL,
  `version_selection_index` DECIMAL(9,6) NULL,
  `unavailable_reason` VARCHAR(500) NULL,
  `calculated_by` BIGINT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE INDEX `uk_task_score_version` (`task_id`, `score_result_version`),
  INDEX `idx_task_score_create_time` (`task_id`, `create_time`),
  INDEX `idx_score_scheme_version` (`weight_scheme_id`, `weight_scheme_version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI评价版本选择指数结果版本';

CREATE TABLE `evaluation_score_result_item` (
  `id` BIGINT NOT NULL,
  `score_result_id` BIGINT NOT NULL,
  `metric_code` VARCHAR(64) NOT NULL,
  `metric_version` VARCHAR(64) NOT NULL,
  `raw_availability` VARCHAR(20) NOT NULL,
  `raw_value` DECIMAL(30,10) NULL,
  `normalization_version` VARCHAR(64) NOT NULL,
  `normalization_availability` VARCHAR(20) NOT NULL,
  `normalized_value` DECIMAL(9,6) NULL,
  `weight_percent` DECIMAL(7,4) NOT NULL,
  `contribution_value` DECIMAL(9,6) NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE INDEX `uk_score_result_metric` (`score_result_id`, `metric_code`),
  INDEX `idx_score_result_item` (`score_result_id`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI评价版本选择指数逐项贡献';
