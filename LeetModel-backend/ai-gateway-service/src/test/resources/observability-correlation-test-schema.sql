DROP TABLE IF EXISTS message_outbox;
CREATE TABLE message_outbox (
  event_id VARCHAR(36) PRIMARY KEY,
  topic VARCHAR(255) NOT NULL,
  tag VARCHAR(80) NOT NULL,
  event_type VARCHAR(100) NOT NULL,
  trace_id VARCHAR(100) NOT NULL,
  status VARCHAR(20) NOT NULL,
  retry_count INT NOT NULL,
  last_error VARCHAR(100),
  occurred_at TIMESTAMP NOT NULL,
  create_time TIMESTAMP NOT NULL,
  update_time TIMESTAMP NOT NULL
);

CREATE INDEX idx_observability_outbox_status
  ON message_outbox(status, create_time);
