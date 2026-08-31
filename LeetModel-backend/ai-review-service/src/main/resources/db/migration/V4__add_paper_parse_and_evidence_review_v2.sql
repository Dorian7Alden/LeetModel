CREATE TABLE `paper_parse_artifact` (
  `id` BIGINT NOT NULL,
  `submission_id` BIGINT NOT NULL,
  `workflow_version` VARCHAR(40) NOT NULL,
  `schema_version` VARCHAR(40) NOT NULL,
  `content_sha256` CHAR(64) NOT NULL,
  `status` VARCHAR(20) NOT NULL,
  `page_count` INT NULL,
  `truncated` TINYINT NOT NULL DEFAULT 0,
  `quality_json` JSON NULL,
  `document_json` JSON NULL,
  `error_message` VARCHAR(500) NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  INDEX `idx_parse_reuse` (`submission_id`, `workflow_version`, `schema_version`, `status`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='不可变 PDF 解析产物';

INSERT INTO `review_version`
(`id`, `version_code`, `name`, `description`, `process_summary`, `final_contract_version`, `status`)
VALUES
(2, 'EVIDENCE_REVIEW_V2', 'V2 证据化 AI 评审',
 '基于完整题面和版本化 PDF 解析产物的可解释平台训练评审',
 'PAPER_PARSE_V1 逐页解析后，按六项固定规则核对题目覆盖、提取页码证据、形成稳定发现并确定性汇总总分',
 'EVIDENCE_REVIEW_V2', 'ENABLED');

CREATE TABLE `review_v2_result` (
  `id` BIGINT NOT NULL,
  `task_id` BIGINT NOT NULL,
  `submission_id` BIGINT NOT NULL,
  `team_id` BIGINT NOT NULL,
  `problem_id` BIGINT NOT NULL,
  `parse_artifact_id` BIGINT NOT NULL,
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
  UNIQUE INDEX `uk_v2_task_id` (`task_id`),
  INDEX `idx_v2_team_create_time` (`team_id`, `create_time`),
  INDEX `idx_v2_parse_artifact` (`parse_artifact_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='V2 证据化评审结果';
