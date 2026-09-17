ALTER TABLE `message_inbox`
  ADD COLUMN `trace_id` VARCHAR(100) NOT NULL DEFAULT 'migration' AFTER `source_service`,
  ADD INDEX `idx_message_inbox_trace` (`trace_id`, `create_time`);
