CREATE TABLE `suggestion_version` (
  `id` BIGINT NOT NULL,
  `version_code` VARCHAR(40) NOT NULL,
  `name` VARCHAR(100) NOT NULL,
  `description` VARCHAR(500) NOT NULL,
  `result_schema_version` VARCHAR(40) NOT NULL,
  `status` VARCHAR(20) NOT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE INDEX `uk_suggestion_version_code` (`version_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='论文建议不可变版本目录';

INSERT INTO `suggestion_version`
(`id`, `version_code`, `name`, `description`, `result_schema_version`, `status`)
VALUES
(1, 'IMPROVEMENT_V1', 'V1 论文改善建议', '题面、评审 JSON 与临时 PDF 文本生成的单份建议', 'IMPROVEMENT_V1', 'ENABLED'),
(2, 'GROUNDED_SUGGESTION_V2', 'V2 有依据的论文建议', '锁定解析、评审发现与知识引用的可重复生成建议', 'GROUNDED_SUGGESTION_V2', 'ENABLED');

ALTER TABLE `suggestion_task`
  DROP INDEX `uk_submission_workflow`,
  ADD COLUMN `client_request_id` VARCHAR(64) NULL AFTER `problem_id`,
  ADD COLUMN `requested_by_user_id` BIGINT NULL AFTER `client_request_id`,
  ADD COLUMN `eligibility_review_task_id` BIGINT NULL AFTER `review_task_id`,
  ADD COLUMN `evidence_review_task_id` BIGINT NULL AFTER `eligibility_review_task_id`,
  ADD COLUMN `review_evidence_projection_version` VARCHAR(40) NULL AFTER `evidence_review_task_id`,
  ADD COLUMN `parse_artifact_id` BIGINT NULL AFTER `review_workflow_version`,
  ADD COLUMN `paper_parsing_workflow_version` VARCHAR(40) NULL AFTER `parse_artifact_id`,
  ADD COLUMN `retrieval_run_id` VARCHAR(64) NULL AFTER `paper_parsing_workflow_version`,
  ADD COLUMN `retrieval_workflow_version` VARCHAR(40) NULL AFTER `retrieval_run_id`,
  ADD COLUMN `knowledge_snapshot_json` JSON NULL AFTER `retrieval_workflow_version`,
  ADD COLUMN `result_schema_version` VARCHAR(40) NULL AFTER `knowledge_snapshot_json`,
  ADD COLUMN `current_stage` VARCHAR(40) NULL AFTER `status`,
  ADD COLUMN `attempt_no` INT NOT NULL DEFAULT 1 AFTER `retry_count`,
  ADD UNIQUE INDEX `uk_requester_client_request` (`requested_by_user_id`, `client_request_id`),
  ADD INDEX `idx_submission_create_time` (`submission_id`, `create_time`),
  ADD INDEX `idx_review_create_time` (`eligibility_review_task_id`, `create_time`);

UPDATE `suggestion_task`
SET `eligibility_review_task_id` = `review_task_id`,
    `evidence_review_task_id` = `review_task_id`,
    `result_schema_version` = 'IMPROVEMENT_V1',
    `current_stage` = CASE WHEN `status` = 'COMPLETED' THEN 'COMPLETED' ELSE `status` END
WHERE `workflow_version` = 'IMPROVEMENT_V1';
