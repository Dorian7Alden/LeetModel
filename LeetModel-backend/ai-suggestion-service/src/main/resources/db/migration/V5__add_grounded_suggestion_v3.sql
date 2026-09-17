INSERT INTO `suggestion_version`
(`id`, `version_code`, `name`, `description`, `result_schema_version`, `status`)
VALUES
(3, 'GROUNDED_SUGGESTION_V3', 'V3 深度证据化与双轨论文建议', '双阶段动态任务编排、按需精准 RAG、改错与升华双轨驱动的高保真建议报告', 'GROUNDED_SUGGESTION_V3', 'ENABLED');

ALTER TABLE `suggestion_task`
  ADD COLUMN `sub_task_summaries_json` JSON NULL AFTER `knowledge_snapshot_json`;
