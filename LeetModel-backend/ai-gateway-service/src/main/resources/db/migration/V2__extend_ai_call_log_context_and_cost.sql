ALTER TABLE `ai_call_log`
  ADD COLUMN `modality` VARCHAR(20) NULL AFTER `scene`,
  ADD COLUMN `caller_service` VARCHAR(64) NULL AFTER `modality`,
  ADD COLUMN `feature_code` VARCHAR(64) NULL AFTER `caller_service`,
  ADD COLUMN `operation_code` VARCHAR(64) NULL AFTER `feature_code`,
  ADD COLUMN `business_task_id` VARCHAR(128) NULL AFTER `operation_code`,
  ADD COLUMN `workflow_version` VARCHAR(64) NULL AFTER `business_task_id`,
  ADD COLUMN `prompt_version` VARCHAR(100) NULL AFTER `workflow_version`,
  ADD COLUMN `model_execution_config_version` VARCHAR(100) NULL AFTER `prompt_version`,
  ADD COLUMN `evaluation_task_id` VARCHAR(128) NULL AFTER `model_execution_config_version`,
  ADD COLUMN `priority` VARCHAR(10) NULL AFTER `evaluation_task_id`,
  ADD COLUMN `idempotency_key` VARCHAR(128) NULL AFTER `priority`,
  ADD COLUMN `deadline` DATETIME(3) NULL AFTER `idempotency_key`,
  ADD COLUMN `provider_response_id` VARCHAR(128) NULL AFTER `model`,
  ADD COLUMN `new_api_request_id` VARCHAR(128) NULL AFTER `provider_response_id`,
  ADD COLUMN `input_tokens` BIGINT NULL AFTER `status`,
  ADD COLUMN `output_tokens` BIGINT NULL AFTER `input_tokens`,
  ADD COLUMN `cache_hit_tokens` BIGINT NULL AFTER `reasoning_tokens`,
  ADD COLUMN `cache_creation_tokens` BIGINT NULL AFTER `cache_hit_tokens`,
  ADD COLUMN `cache_miss_tokens` BIGINT NULL AFTER `cache_creation_tokens`,
  ADD COLUMN `usage_completeness` VARCHAR(20) NULL AFTER `usage_complete`,
  ADD COLUMN `queue_ms` BIGINT NULL AFTER `usage_completeness`,
  ADD COLUMN `execution_ms` BIGINT NULL AFTER `queue_ms`,
  ADD COLUMN `total_ms` BIGINT NULL AFTER `execution_ms`,
  ADD COLUMN `cost_amount` DECIMAL(24,12) NULL AFTER `total_ms`,
  ADD COLUMN `cost_currency` CHAR(3) NULL AFTER `cost_amount`,
  ADD COLUMN `cost_source` VARCHAR(40) NULL AFTER `cost_currency`,
  ADD COLUMN `price_snapshot_version` VARCHAR(100) NULL AFTER `cost_source`,
  ADD COLUMN `cost_completeness` VARCHAR(20) NULL AFTER `price_snapshot_version`;

UPDATE `ai_call_log`
   SET `modality` = CASE
           WHEN `scene` IN ('GENERAL_TEXT', 'TEXT') THEN 'TEXT'
           WHEN `scene` = 'MULTIMODAL' THEN 'MULTIMODAL'
           ELSE NULL
       END,
       `caller_service` = 'LEGACY',
       `feature_code` = 'LEGACY',
       `operation_code` = 'LEGACY_CHAT',
       `idempotency_key` = CONCAT('legacy-call:', `call_id`),
       `input_tokens` = `prompt_tokens`,
       `output_tokens` = `completion_tokens`,
       `usage_completeness` = CASE
           WHEN `usage_complete` = 1 THEN 'COMPLETE'
           WHEN `prompt_tokens` IS NOT NULL OR `completion_tokens` IS NOT NULL
                OR `reasoning_tokens` IS NOT NULL OR `total_tokens` IS NOT NULL THEN 'PARTIAL'
           ELSE 'UNKNOWN'
       END,
       `queue_ms` = 0,
       `execution_ms` = `duration_ms`,
       `total_ms` = `duration_ms`,
       `cost_source` = 'UNKNOWN',
       `cost_completeness` = 'UNKNOWN'
 WHERE `caller_service` IS NULL;

CREATE INDEX `idx_business_task_time`
    ON `ai_call_log` (`caller_service`, `business_task_id`, `create_time`);
CREATE INDEX `idx_evaluation_task_time`
    ON `ai_call_log` (`evaluation_task_id`, `create_time`);
CREATE INDEX `idx_feature_operation_time`
    ON `ai_call_log` (`feature_code`, `operation_code`, `create_time`);
CREATE INDEX `idx_modality_time`
    ON `ai_call_log` (`modality`, `create_time`);
