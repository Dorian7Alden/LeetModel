DROP TABLE IF EXISTS ai_call_log;
CREATE TABLE ai_call_log (
  id BIGINT PRIMARY KEY,
  call_id VARCHAR(64) NOT NULL UNIQUE,
  scene VARCHAR(30), modality VARCHAR(20), caller_service VARCHAR(64),
  feature_code VARCHAR(64), operation_code VARCHAR(64), business_task_id VARCHAR(128),
  workflow_version VARCHAR(64), prompt_version VARCHAR(100),
  model_execution_config_version VARCHAR(100), evaluation_task_id VARCHAR(128),
  priority VARCHAR(10), idempotency_key VARCHAR(128), deadline TIMESTAMP,
  provider VARCHAR(30), model VARCHAR(100), provider_response_id VARCHAR(128),
  new_api_request_id VARCHAR(128), status VARCHAR(20),
  input_tokens BIGINT, output_tokens BIGINT, prompt_tokens BIGINT, completion_tokens BIGINT,
  reasoning_tokens BIGINT, cache_hit_tokens BIGINT, cache_creation_tokens BIGINT,
  cache_miss_tokens BIGINT, total_tokens BIGINT, usage_complete BOOLEAN,
  usage_completeness VARCHAR(20), queue_ms BIGINT, execution_ms BIGINT, total_ms BIGINT,
  duration_ms BIGINT, cost_amount DECIMAL(24,12), cost_currency CHAR(3),
  cost_source VARCHAR(40), price_snapshot_version VARCHAR(100), cost_completeness VARCHAR(20),
  cost_enrichment_status VARCHAR(20), cost_enrichment_attempts INT DEFAULT 0,
  cost_next_retry_at TIMESTAMP, cost_last_attempt_at TIMESTAMP,
  error_code INT, error_message VARCHAR(300),
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  deleted BOOLEAN DEFAULT FALSE
);
