ALTER TABLE `ai_call_log`
  ADD COLUMN `call_type` VARCHAR(20) NULL AFTER `call_id`,
  ADD COLUMN `input_count` INT NULL AFTER `usage_completeness`,
  ADD COLUMN `vector_dimension` INT NULL AFTER `input_count`;

UPDATE `ai_call_log`
   SET `call_type` = 'CHAT'
 WHERE `call_type` IS NULL;

CREATE INDEX `idx_call_type_time`
    ON `ai_call_log` (`call_type`, `create_time`);
