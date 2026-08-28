DROP TABLE IF EXISTS ai_call_log;
CREATE TABLE ai_call_log (
  id BIGINT PRIMARY KEY,
  call_id VARCHAR(64) NOT NULL UNIQUE,
  call_type VARCHAR(20),
  scene VARCHAR(30), modality VARCHAR(20), caller_service VARCHAR(64),
  feature_code VARCHAR(64), operation_code VARCHAR(64), business_task_id VARCHAR(128),
  workflow_version VARCHAR(64), prompt_version VARCHAR(100),
  model_execution_config_version VARCHAR(100), evaluation_task_id VARCHAR(128),
  rag_index_version VARCHAR(128),
  priority VARCHAR(10), idempotency_key VARCHAR(128), deadline TIMESTAMP,
  provider VARCHAR(30), model VARCHAR(100), provider_response_id VARCHAR(128),
  new_api_request_id VARCHAR(128), status VARCHAR(20),
  input_tokens BIGINT, output_tokens BIGINT, prompt_tokens BIGINT, completion_tokens BIGINT,
  reasoning_tokens BIGINT, cache_hit_tokens BIGINT, cache_creation_tokens BIGINT,
  cache_miss_tokens BIGINT, total_tokens BIGINT, usage_complete BOOLEAN,
  usage_completeness VARCHAR(20), queue_ms BIGINT, execution_ms BIGINT, total_ms BIGINT,
  input_count INT, vector_dimension INT,
  duration_ms BIGINT, cost_amount DECIMAL(24,12), cost_currency CHAR(3),
  cost_source VARCHAR(40), price_snapshot_version VARCHAR(100), cost_completeness VARCHAR(20),
  cost_enrichment_status VARCHAR(20), cost_enrichment_attempts INT DEFAULT 0,
  cost_next_retry_at TIMESTAMP, cost_last_attempt_at TIMESTAMP,
  error_code INT, error_message VARCHAR(300),
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  deleted BOOLEAN DEFAULT FALSE
);

DROP TABLE IF EXISTS ai_call_attempt;
DROP TABLE IF EXISTS ai_call_task;
CREATE TABLE ai_call_task (
  id BIGINT PRIMARY KEY, task_id VARCHAR(64) NOT NULL UNIQUE, call_id VARCHAR(64) NOT NULL UNIQUE,
  caller_service VARCHAR(64) NOT NULL, idempotency_key VARCHAR(128) NOT NULL,
  call_type VARCHAR(20) NOT NULL, feature_code VARCHAR(64) NOT NULL, operation_code VARCHAR(64) NOT NULL,
  declared_priority VARCHAR(10) NOT NULL, effective_priority VARCHAR(10) NOT NULL,
  state VARCHAR(20) NOT NULL, model_execution_config_version VARCHAR(100),
  request_hash CHAR(64) NOT NULL, request_payload CLOB NOT NULL, result_payload CLOB,
  deadline TIMESTAMP NOT NULL, max_queue_wait_ms BIGINT NOT NULL,
  lease_owner VARCHAR(100), lease_expiry TIMESTAMP, attempt_count INT DEFAULT 0,
  version BIGINT DEFAULT 0, cancel_requested BOOLEAN DEFAULT FALSE,
  error_code VARCHAR(64), dead_letter_reason VARCHAR(100), queued_at TIMESTAMP NOT NULL,
  leased_at TIMESTAMP, started_at TIMESTAMP, finished_at TIMESTAMP,
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP, update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  deleted BOOLEAN DEFAULT FALSE,
  CONSTRAINT uk_task_caller_idempotency UNIQUE (caller_service, idempotency_key)
);
CREATE TABLE ai_call_attempt (
  id BIGINT PRIMARY KEY, attempt_id VARCHAR(64) NOT NULL UNIQUE, task_id VARCHAR(64) NOT NULL,
  attempt_no INT NOT NULL, state VARCHAR(20) NOT NULL, owner VARCHAR(100) NOT NULL,
  new_api_request_id VARCHAR(128), http_status INT, error_code VARCHAR(64), retry_after_ms BIGINT,
  prepared_at TIMESTAMP NOT NULL, sent_at TIMESTAMP, acknowledged_at TIMESTAMP, finished_at TIMESTAMP,
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP, update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  deleted BOOLEAN DEFAULT FALSE, CONSTRAINT uk_attempt_task_no UNIQUE (task_id, attempt_no)
);
