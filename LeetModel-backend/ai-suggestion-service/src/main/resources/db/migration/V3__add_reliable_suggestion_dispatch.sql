CREATE TABLE `message_inbox` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `consumer_group` VARCHAR(255) NOT NULL,
  `event_id` VARCHAR(36) NOT NULL,
  `event_type` VARCHAR(100) NOT NULL,
  `source_service` VARCHAR(100) NOT NULL,
  `status` VARCHAR(20) NOT NULL,
  `occurred_at` DATETIME(3) NOT NULL,
  `consumed_at` DATETIME(3) NULL,
  `create_time` DATETIME(3) NOT NULL,
  `update_time` DATETIME(3) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE INDEX `uk_message_inbox_consumer_event` (`consumer_group`, `event_id`),
  INDEX `idx_message_inbox_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='建议服务消费 Inbox';

CREATE TABLE `message_outbox` (
  `event_id` VARCHAR(36) NOT NULL,
  `topic` VARCHAR(255) NOT NULL,
  `tag` VARCHAR(80) NOT NULL,
  `message_key` VARCHAR(255) NOT NULL,
  `event_type` VARCHAR(100) NOT NULL,
  `schema_version` INT NOT NULL,
  `source_service` VARCHAR(100) NOT NULL,
  `aggregate_type` VARCHAR(100) NOT NULL,
  `aggregate_id` VARCHAR(100) NOT NULL,
  `idempotency_key` VARCHAR(255) NOT NULL,
  `trace_id` VARCHAR(100) NOT NULL,
  `payload_json` LONGTEXT NOT NULL,
  `status` VARCHAR(20) NOT NULL,
  `retry_count` INT NOT NULL DEFAULT 0,
  `next_attempt_at` DATETIME(3) NOT NULL,
  `lease_owner` VARCHAR(160) NULL,
  `lease_expires_at` DATETIME(3) NULL,
  `broker_message_id` VARCHAR(255) NULL,
  `last_error` VARCHAR(500) NULL,
  `occurred_at` DATETIME(3) NOT NULL,
  `published_at` DATETIME(3) NULL,
  `create_time` DATETIME(3) NOT NULL,
  `update_time` DATETIME(3) NOT NULL,
  PRIMARY KEY (`event_id`),
  UNIQUE INDEX `uk_outbox_event_idempotency` (`event_type`, `idempotency_key`),
  INDEX `idx_message_outbox_dispatch` (`status`, `next_attempt_at`, `lease_expires_at`, `create_time`),
  INDEX `idx_message_outbox_aggregate` (`aggregate_type`, `aggregate_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='建议服务可靠消息 Outbox';

ALTER TABLE `suggestion_task`
  ADD COLUMN `max_attempts` INT NOT NULL DEFAULT 3 AFTER `attempt_no`,
  ADD COLUMN `trace_id` VARCHAR(100) NOT NULL DEFAULT 'migration' AFTER `max_attempts`,
  ADD COLUMN `lease_owner` VARCHAR(160) NULL AFTER `trace_id`,
  ADD COLUMN `lease_token` VARCHAR(36) NULL AFTER `lease_owner`,
  ADD COLUMN `lease_expires_at` DATETIME(3) NULL AFTER `lease_token`,
  ADD COLUMN `heartbeat_at` DATETIME(3) NULL AFTER `lease_expires_at`,
  ADD COLUMN `recovery_count` INT NOT NULL DEFAULT 0 AFTER `heartbeat_at`,
  ADD COLUMN `failure_type` VARCHAR(40) NULL AFTER `recovery_count`,
  ADD COLUMN `ai_idempotency_key` VARCHAR(128) NULL AFTER `failure_type`,
  ADD COLUMN `last_wakeup_at` DATETIME(3) NULL AFTER `ai_idempotency_key`,
  ADD COLUMN `last_wakeup_event_at` DATETIME(3) NULL AFTER `last_wakeup_at`,
  ADD INDEX `idx_suggestion_task_claim` (`status`, `next_run_at`, `lease_expires_at`, `create_time`),
  ADD INDEX `idx_suggestion_task_lease` (`lease_owner`, `lease_token`, `lease_expires_at`),
  ADD INDEX `idx_suggestion_task_reconcile` (`status`, `next_run_at`, `last_wakeup_event_at`);

UPDATE `suggestion_task`
SET `ai_idempotency_key` = CONCAT('suggestion:task:', `id`, ':attempt:', `attempt_no`)
WHERE `ai_idempotency_key` IS NULL;
