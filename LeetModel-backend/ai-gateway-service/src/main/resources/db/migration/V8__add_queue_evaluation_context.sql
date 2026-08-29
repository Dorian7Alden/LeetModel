ALTER TABLE `ai_call_task`
  ADD COLUMN `evaluation_task_id` VARCHAR(128) NULL AFTER `model_execution_config_version`,
  ADD INDEX `idx_queue_evaluation_task` (`evaluation_task_id`, `state`, `queued_at`);
