-- 题目收藏表：记录用户收藏题目事实与收藏时刻
CREATE TABLE IF NOT EXISTS problem_favorite (
    id BIGINT NOT NULL COMMENT '主键 ID（雪花算法）',
    user_id BIGINT NOT NULL COMMENT '用户 ID',
    problem_id BIGINT NOT NULL COMMENT '题目 ID',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '收藏时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_problem (user_id, problem_id),
    KEY idx_user_create_time (user_id, create_time ASC),
    KEY idx_problem_id (problem_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='题目收藏关系表';
