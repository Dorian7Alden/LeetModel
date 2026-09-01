CREATE TABLE `message_inbox` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `consumer_group` VARCHAR(255) NOT NULL,
  `event_id` VARCHAR(36) NOT NULL,
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
  INDEX `idx_message_inbox_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='提交服务消费 Inbox（预留统一运维）';
