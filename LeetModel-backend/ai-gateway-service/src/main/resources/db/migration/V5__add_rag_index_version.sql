ALTER TABLE `ai_call_log`
  ADD COLUMN `rag_index_version` VARCHAR(128) NULL AFTER `evaluation_task_id`;

CREATE INDEX `idx_rag_index_version_time`
    ON `ai_call_log` (`rag_index_version`, `create_time`);
