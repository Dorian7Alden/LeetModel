ALTER TABLE `evaluation_task`
  ADD COLUMN `last_operated_by` BIGINT NULL AFTER `error_message`,
  ADD COLUMN `last_operation` VARCHAR(20) NULL AFTER `last_operated_by`,
  ADD COLUMN `last_operated_at` DATETIME NULL AFTER `last_operation`;

ALTER TABLE `evaluation_task`
  ADD INDEX `idx_task_status_update_time` (`status`, `update_time`),
  ADD INDEX `idx_task_last_operator` (`last_operated_by`, `last_operated_at`);
