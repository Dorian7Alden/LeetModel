CREATE TABLE message_outbox (
    event_id VARCHAR(36) PRIMARY KEY,
    topic VARCHAR(255) NOT NULL,
    tag VARCHAR(80) NOT NULL,
    message_key VARCHAR(255) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    schema_version INT NOT NULL,
    source_service VARCHAR(100) NOT NULL,
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id VARCHAR(100) NOT NULL,
    idempotency_key VARCHAR(255) NOT NULL,
    trace_id VARCHAR(100) NOT NULL,
    payload_json TEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    retry_count INT NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP NOT NULL,
    lease_owner VARCHAR(160),
    lease_expires_at TIMESTAMP,
    broker_message_id VARCHAR(255),
    last_error VARCHAR(500),
    occurred_at TIMESTAMP NOT NULL,
    published_at TIMESTAMP,
    create_time TIMESTAMP NOT NULL,
    update_time TIMESTAMP NOT NULL
);

CREATE INDEX idx_message_outbox_dispatch
    ON message_outbox(status, next_attempt_at, lease_expires_at, create_time);

CREATE TABLE message_inbox (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    consumer_group VARCHAR(255) NOT NULL,
    event_id VARCHAR(36) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    source_service VARCHAR(100) NOT NULL,
    trace_id VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL,
    occurred_at TIMESTAMP NOT NULL,
    consumed_at TIMESTAMP,
    create_time TIMESTAMP NOT NULL,
    update_time TIMESTAMP NOT NULL,
    CONSTRAINT uk_message_inbox_consumer_event UNIQUE (consumer_group, event_id)
);

CREATE TABLE domain_probe (
    id VARCHAR(36) PRIMARY KEY
);
