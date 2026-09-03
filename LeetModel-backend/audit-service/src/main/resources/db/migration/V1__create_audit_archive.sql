CREATE TABLE `message_inbox` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `consumer_group` VARCHAR(255) NOT NULL,
  `event_id` VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `event_type` VARCHAR(100) NOT NULL,
  `source_service` VARCHAR(100) NOT NULL,
  `trace_id` VARCHAR(100) NOT NULL,
  `status` VARCHAR(20) NOT NULL,
  `occurred_at` DATETIME(3) NOT NULL,
  `consumed_at` DATETIME(3) NULL,
  `create_time` DATETIME(3) NOT NULL,
  `update_time` DATETIME(3) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE INDEX `uk_message_inbox_consumer_event` (`consumer_group`, `event_id`),
  INDEX `idx_message_inbox_trace` (`trace_id`, `create_time`),
  INDEX `idx_message_inbox_status_time` (`status`, `create_time`),
  CONSTRAINT `chk_message_inbox_status`
    CHECK (`status` IN ('PROCESSING', 'CONSUMED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='操作审计消费 Inbox';

CREATE TABLE `operation_audit_event` (
  `audit_event_id` VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `audit_schema_version` INT NOT NULL,
  `operation_id` VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `phase` VARCHAR(20) NOT NULL,
  `occurred_at` DATETIME(3) NOT NULL,
  `source_service` VARCHAR(100) NOT NULL,
  `service_version` VARCHAR(100) NOT NULL,
  `category` VARCHAR(64) NOT NULL,
  `operation_code` VARCHAR(100) NOT NULL,
  `risk_level` VARCHAR(16) NOT NULL,
  `outcome` VARCHAR(16) NOT NULL,
  `reason` VARCHAR(300) NULL,
  `failure_code` VARCHAR(100) NULL,
  `actor_type` VARCHAR(16) NOT NULL,
  `actor_id` VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `actor_roles_json` JSON NOT NULL,
  `target_type` VARCHAR(64) NOT NULL,
  `target_id` VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `target_version` VARCHAR(100) NULL,
  `before_summary_json` JSON NOT NULL,
  `after_summary_json` JSON NOT NULL,
  `trace_id` VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `sw_trace_id` VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NULL,
  `request_id` VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NULL,
  `domain_task_id` VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NULL,
  `related_event_id` VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NULL,
  `client_ip_hash` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
  `user_agent_hash` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
  `archived_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`audit_event_id`),
  INDEX `idx_audit_operation_timeline` (`operation_id`, `occurred_at`, `audit_event_id`),
  INDEX `idx_audit_actor_timeline` (`actor_id`, `occurred_at`, `audit_event_id`),
  INDEX `idx_audit_target_timeline` (`target_type`, `target_id`, `occurred_at`, `audit_event_id`),
  INDEX `idx_audit_operation_outcome_time` (`operation_code`, `outcome`, `occurred_at`),
  INDEX `idx_audit_source_time` (`source_service`, `occurred_at`),
  INDEX `idx_audit_trace` (`trace_id`),
  INDEX `idx_audit_sw_trace` (`sw_trace_id`),
  CONSTRAINT `chk_audit_schema_version` CHECK (`audit_schema_version` = 1),
  CONSTRAINT `chk_audit_phase_outcome` CHECK (
    (`phase` = 'REQUESTED' AND `outcome` = 'PENDING') OR
    (`phase` = 'COMPLETED' AND `outcome` IN ('SUCCEEDED', 'FAILED', 'REJECTED'))
  ),
  CONSTRAINT `chk_audit_risk_level` CHECK (`risk_level` IN ('LOW', 'MEDIUM', 'HIGH')),
  CONSTRAINT `chk_audit_failure_code` CHECK (
    (`outcome` IN ('FAILED', 'REJECTED') AND `failure_code` IS NOT NULL) OR
    (`outcome` IN ('PENDING', 'SUCCEEDED') AND `failure_code` IS NULL)
  ),
  CONSTRAINT `chk_audit_actor_type` CHECK (`actor_type` IN ('USER', 'ADMIN', 'SERVICE', 'SYSTEM')),
  CONSTRAINT `chk_audit_actor_roles_json` CHECK (JSON_TYPE(`actor_roles_json`) = 'ARRAY'),
  CONSTRAINT `chk_audit_before_summary_json` CHECK (JSON_TYPE(`before_summary_json`) = 'OBJECT'),
  CONSTRAINT `chk_audit_after_summary_json` CHECK (JSON_TYPE(`after_summary_json`) = 'OBJECT'),
  CONSTRAINT `chk_audit_client_ip_hash` CHECK (
    `client_ip_hash` IS NULL OR `client_ip_hash` REGEXP '^[0-9A-Fa-f]{64}$'
  ),
  CONSTRAINT `chk_audit_user_agent_hash` CHECK (
    `user_agent_hash` IS NULL OR `user_agent_hash` REGEXP '^[0-9A-Fa-f]{64}$'
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='只追加操作审计事实';
