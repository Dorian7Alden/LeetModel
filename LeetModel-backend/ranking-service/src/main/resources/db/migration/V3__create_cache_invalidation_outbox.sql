CREATE TABLE cache_invalidation_outbox (
    id VARCHAR(36) NOT NULL,
    revision BIGINT NOT NULL AUTO_INCREMENT,
    owner_service VARCHAR(64) NOT NULL,
    region VARCHAR(64) NOT NULL,
    scope_key VARCHAR(128) NOT NULL,
    schema_version VARCHAR(32) NOT NULL,
    occurred_at DATETIME(3) NOT NULL,
    delivered_at DATETIME(3) NULL,
    retry_count INT NOT NULL DEFAULT 0,
    next_attempt_at DATETIME(3) NOT NULL,
    last_error VARCHAR(128) NULL,
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_cache_invalidation_revision (revision),
    KEY idx_delivered_next_attempt (delivered_at, next_attempt_at),
    KEY idx_region_scope_revision (region, scope_key, revision)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
