ALTER TABLE `ai_call_log`
  ADD COLUMN `cost_enrichment_status` VARCHAR(20) NULL AFTER `cost_completeness`,
  ADD COLUMN `cost_enrichment_attempts` INT NOT NULL DEFAULT 0 AFTER `cost_enrichment_status`,
  ADD COLUMN `cost_next_retry_at` DATETIME(3) NULL AFTER `cost_enrichment_attempts`,
  ADD COLUMN `cost_last_attempt_at` DATETIME(3) NULL AFTER `cost_next_retry_at`;

UPDATE `ai_call_log`
   SET `cost_enrichment_status` = CASE
           WHEN `cost_source` IN ('NEW_API_ACTUAL', 'PRICE_SNAPSHOT_ESTIMATED') THEN 'COMPLETED'
           ELSE 'PENDING'
       END,
       `cost_next_retry_at` = CASE
           WHEN `cost_source` = 'UNKNOWN' THEN CURRENT_TIMESTAMP(3)
           ELSE NULL
       END
 WHERE `cost_enrichment_status` IS NULL;

CREATE INDEX `idx_cost_enrichment_due`
    ON `ai_call_log` (`cost_enrichment_status`, `cost_next_retry_at`, `create_time`);
