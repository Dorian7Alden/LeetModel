ALTER TABLE `ai_call_task`
  ADD COLUMN `trace_id` VARCHAR(100) NOT NULL DEFAULT 'migration' AFTER `call_id`,
  ADD INDEX `idx_task_trace` (`trace_id`, `create_time`);

ALTER TABLE `ai_call_log`
  ADD COLUMN `trace_id` VARCHAR(100) NOT NULL DEFAULT 'migration' AFTER `call_id`,
  ADD INDEX `idx_call_trace` (`trace_id`, `create_time`);
