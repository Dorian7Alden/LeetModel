ALTER TABLE `message_outbox`
  ADD UNIQUE INDEX `uk_outbox_event_idempotency` (`event_type`, `idempotency_key`),
  ADD INDEX `idx_message_outbox_aggregate` (`aggregate_type`, `aggregate_id`, `create_time`);
