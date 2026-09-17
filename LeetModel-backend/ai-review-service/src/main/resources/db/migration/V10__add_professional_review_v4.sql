INSERT INTO `review_version`
(`id`, `version_code`, `name`, `description`, `process_summary`, `final_contract_version`, `status`)
VALUES
(4, 'DEEP_EVIDENCE_REVIEW_V4', 'V4 专业证据化 AI 评审',
 '基于 V3 多阶段评审增加 Markdown 解释、真实原文、优点问题双向评价与确定性排序',
 '复用 PAPER_DOCUMENT_V2 与 V3 评分流水线，服务端补全原文和知识依据并生成专业报告',
 'DEEP_EVIDENCE_REVIEW_V4', 'ENABLED');

CREATE TABLE `review_v4_result` (
  `id` BIGINT NOT NULL COMMENT '结果唯一雪花 ID',
  `task_id` BIGINT NOT NULL COMMENT '评审任务 ID',
  `submission_id` BIGINT NOT NULL COMMENT '提交 ID',
  `team_id` BIGINT NOT NULL COMMENT '队伍 ID',
  `problem_id` BIGINT NOT NULL COMMENT '题目 ID',
  `parse_artifact_id` BIGINT NOT NULL COMMENT 'PAPER_DOCUMENT_V2 产物 ID',
  `workflow_version` VARCHAR(40) NOT NULL,
  `result_schema_version` VARCHAR(40) NOT NULL,
  `scoring_rule_version` VARCHAR(40) NOT NULL,
  `score` DECIMAL(5,2) NOT NULL,
  `result_json` JSON NOT NULL,
  `model_name` VARCHAR(100) NULL,
  `ai_call_id` VARCHAR(64) NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE INDEX `uk_v4_task_id` (`task_id`),
  INDEX `idx_v4_submission_id` (`submission_id`),
  INDEX `idx_v4_team_create_time` (`team_id`, `create_time`),
  INDEX `idx_v4_parse_artifact` (`parse_artifact_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='V4 专业证据化评审结果表';
