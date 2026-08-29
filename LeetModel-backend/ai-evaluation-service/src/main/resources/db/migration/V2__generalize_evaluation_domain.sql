ALTER TABLE `evaluation_dataset`
  ADD COLUMN `feature_code` VARCHAR(32) NULL AFTER `id`,
  ADD COLUMN `dataset_version` VARCHAR(64) NULL AFTER `feature_code`,
  ADD COLUMN `sample_schema_version` VARCHAR(40) NULL AFTER `dataset_version`;

UPDATE `evaluation_dataset`
SET `feature_code` = 'REVIEW',
    `dataset_version` = CONCAT('LEGACY_REVIEW_', `id`),
    `sample_schema_version` = 'REVIEW_SUBMISSION_V1'
WHERE `feature_code` IS NULL;

ALTER TABLE `evaluation_dataset`
  MODIFY COLUMN `feature_code` VARCHAR(32) NOT NULL,
  MODIFY COLUMN `dataset_version` VARCHAR(64) NOT NULL,
  MODIFY COLUMN `sample_schema_version` VARCHAR(40) NOT NULL,
  ADD UNIQUE INDEX `uk_evaluation_dataset_feature_version` (`feature_code`, `dataset_version`),
  ADD INDEX `idx_feature_create_time` (`feature_code`, `create_time`);

ALTER TABLE `evaluation_sample`
  ADD COLUMN `sample_type` VARCHAR(40) NULL AFTER `dataset_id`,
  ADD COLUMN `payload_schema_version` VARCHAR(40) NULL AFTER `sample_type`,
  ADD COLUMN `payload_json` LONGTEXT NULL AFTER `payload_schema_version`;

UPDATE `evaluation_sample`
SET `sample_type` = 'SUBMISSION_REFERENCE',
    `payload_schema_version` = 'REVIEW_SUBMISSION_V1',
    `payload_json` = CONCAT('{"submissionId":', `submission_id`, '}')
WHERE `sample_type` IS NULL;

ALTER TABLE `evaluation_sample`
  MODIFY COLUMN `sample_type` VARCHAR(40) NOT NULL,
  MODIFY COLUMN `payload_schema_version` VARCHAR(40) NOT NULL,
  MODIFY COLUMN `payload_json` LONGTEXT NOT NULL,
  MODIFY COLUMN `submission_id` BIGINT NULL,
  MODIFY COLUMN `team_id` BIGINT NULL,
  MODIFY COLUMN `problem_id` BIGINT NULL,
  ADD INDEX `idx_dataset_sample_type` (`dataset_id`, `sample_type`, `sort_order`);

ALTER TABLE `evaluation_task`
  ADD COLUMN `feature_code` VARCHAR(32) NULL AFTER `dataset_id`,
  ADD COLUMN `model_execution_config_version` VARCHAR(64) NULL AFTER `workflow_version`,
  ADD COLUMN `rag_index_version` VARCHAR(100) NULL AFTER `model_execution_config_version`,
  ADD COLUMN `metric_set_version` VARCHAR(40) NULL AFTER `rag_index_version`,
  ADD COLUMN `workflow_snapshot_json` LONGTEXT NULL AFTER `metric_set_version`,
  ADD COLUMN `metric_definition_snapshot_json` LONGTEXT NULL AFTER `workflow_snapshot_json`;

UPDATE `evaluation_task`
SET `feature_code` = 'REVIEW',
    `model_execution_config_version` = 'MODEL_CFG_REVIEW_MULTIMODAL_0001',
    `metric_set_version` = 'LEGACY_REVIEW_METRICS_V1',
    `workflow_snapshot_json` = CONCAT('{"featureCode":"REVIEW","workflowVersion":"',
                                      `workflow_version`, '","legacy":true}'),
    `metric_definition_snapshot_json` = '{"metricSetVersion":"LEGACY_REVIEW_METRICS_V1","legacyOverallScore":true}'
WHERE `feature_code` IS NULL;

ALTER TABLE `evaluation_task`
  MODIFY COLUMN `feature_code` VARCHAR(32) NOT NULL,
  MODIFY COLUMN `model_execution_config_version` VARCHAR(64) NOT NULL,
  MODIFY COLUMN `metric_set_version` VARCHAR(40) NOT NULL,
  MODIFY COLUMN `workflow_snapshot_json` LONGTEXT NOT NULL,
  MODIFY COLUMN `metric_definition_snapshot_json` LONGTEXT NOT NULL,
  ADD INDEX `idx_feature_dataset_status` (`feature_code`, `dataset_id`, `status`, `create_time`),
  ADD INDEX `idx_metric_set_version` (`metric_set_version`, `create_time`);

ALTER TABLE `evaluation_run_attempt`
  ADD COLUMN `slot_key` VARCHAR(128) NULL AFTER `attempt_no`,
  ADD COLUMN `experiment_run_id` VARCHAR(128) NULL AFTER `slot_key`,
  ADD COLUMN `model_execution_config_version` VARCHAR(64) NULL AFTER `model_name`,
  ADD COLUMN `rag_index_version` VARCHAR(100) NULL AFTER `model_execution_config_version`,
  ADD COLUMN `metrics_json` LONGTEXT NULL AFTER `result_json`;

UPDATE `evaluation_run_attempt`
SET `slot_key` = CONCAT(`task_id`, ':', `sample_id`, ':', `repetition_no`),
    `experiment_run_id` = CONCAT('review-eval:', `task_id`, ':', `sample_id`, ':', `repetition_no`),
    `model_execution_config_version` = 'MODEL_CFG_REVIEW_MULTIMODAL_0001'
WHERE `slot_key` IS NULL;

ALTER TABLE `evaluation_run_attempt`
  MODIFY COLUMN `slot_key` VARCHAR(128) NOT NULL,
  MODIFY COLUMN `experiment_run_id` VARCHAR(128) NOT NULL,
  MODIFY COLUMN `model_execution_config_version` VARCHAR(64) NOT NULL,
  ADD UNIQUE INDEX `uk_evaluation_run_slot_attempt` (`task_id`, `slot_key`, `attempt_no`),
  ADD INDEX `idx_task_slot_attempt` (`task_id`, `slot_key`, `attempt_no`),
  ADD INDEX `idx_experiment_run_id` (`experiment_run_id`),
  ADD INDEX `idx_ai_call_id` (`ai_call_id`);
