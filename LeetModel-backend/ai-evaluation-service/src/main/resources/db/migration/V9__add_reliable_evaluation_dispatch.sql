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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评价服务消费 Inbox';

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评价服务可靠消息 Outbox';

ALTER TABLE `evaluation_task`
  ADD COLUMN `trace_id` VARCHAR(100) NOT NULL DEFAULT 'migration' AFTER `client_request_id`;

ALTER TABLE `evaluation_run_attempt`
  ADD COLUMN `next_run_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) AFTER `status`,
  ADD COLUMN `lease_owner` VARCHAR(160) NULL AFTER `next_run_at`,
  ADD COLUMN `lease_token` VARCHAR(36) NULL AFTER `lease_owner`,
  ADD COLUMN `lease_expires_at` DATETIME(3) NULL AFTER `lease_token`,
  ADD COLUMN `heartbeat_at` DATETIME(3) NULL AFTER `lease_expires_at`,
  ADD COLUMN `recovery_count` INT NOT NULL DEFAULT 0 AFTER `heartbeat_at`,
  ADD COLUMN `last_wakeup_at` DATETIME(3) NULL AFTER `recovery_count`,
  ADD COLUMN `last_wakeup_event_at` DATETIME(3) NULL AFTER `last_wakeup_at`,
  ADD INDEX `idx_evaluation_run_claim` (`status`, `next_run_at`, `lease_expires_at`, `create_time`),
  ADD INDEX `idx_evaluation_run_lease` (`lease_owner`, `lease_token`, `lease_expires_at`),
  ADD INDEX `idx_evaluation_run_reconcile` (`status`, `next_run_at`, `last_wakeup_event_at`);
