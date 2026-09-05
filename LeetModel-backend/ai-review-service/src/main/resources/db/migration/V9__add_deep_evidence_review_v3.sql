-- 注册 V3 评审版本元数据
INSERT INTO `review_version`
(`id`, `version_code`, `name`, `description`, `process_summary`, `final_contract_version`, `status`)
VALUES
(3, 'DEEP_EVIDENCE_REVIEW_V3', 'V3 深度证据化 AI 评审',
 '基于第二代结构化解析 PAPER_DOCUMENT_V2 与双阶段流水线的深度科学评审',
 '消费原生 LaTeX/HTML表格/代码，阶段一静态规范审查 + 阶段二动态小题并发推导，五维量表与服务端确定性求和',
 'DEEP_EVIDENCE_REVIEW_V3', 'ENABLED');

-- 创建 V3 评审结果持久化表
CREATE TABLE `review_v3_result` (
  `id` BIGINT NOT NULL COMMENT '结果唯一雪花 ID',
  `task_id` BIGINT NOT NULL COMMENT '评审调度任务 ID',
  `submission_id` BIGINT NOT NULL COMMENT '参赛提交 ID',
  `team_id` BIGINT NOT NULL COMMENT '参赛队伍 ID',
  `problem_id` BIGINT NOT NULL COMMENT '赛题 ID',
  `parse_artifact_id` BIGINT NOT NULL COMMENT '引用的 PAPER_DOCUMENT_V2 产物 ID',
  `workflow_version` VARCHAR(40) NOT NULL COMMENT '工作流版本: DEEP_EVIDENCE_REVIEW_V3',
  `result_schema_version` VARCHAR(40) NOT NULL COMMENT '契约版本: DEEP_EVIDENCE_REVIEW_V3',
  `scoring_rule_version` VARCHAR(40) NOT NULL COMMENT '评分量表版本: MODELING_TRAINING_RUBRIC_V3',
  `score` DECIMAL(5,2) NOT NULL COMMENT '全局最终总得分 (0.00~100.00)',
  `phase1_score` DECIMAL(5,2) NOT NULL COMMENT '阶段一规范性得分 (0.00~25.00)',
  `phase2_score` DECIMAL(5,2) NOT NULL COMMENT '阶段二内容推演得分 (0.00~75.00)',
  `phase1_json` JSON NULL COMMENT '阶段一静态审查结构化快照',
  `task_plan_json` JSON NULL COMMENT '阶段二动态任务规划快照',
  `phase2_json` JSON NULL COMMENT '阶段二并发子任务原始执行明细列表快照',
  `result_json` JSON NOT NULL COMMENT '终态全局交付结构 DeepEvidenceReviewV3Output',
  `model_name` VARCHAR(100) NULL COMMENT '主力模型名称',
  `ai_call_id` VARCHAR(64) NULL COMMENT '链路追踪 callId',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记 (0正常 1删除)',
  PRIMARY KEY (`id`),
  UNIQUE INDEX `uk_v3_task_id` (`task_id`),
  INDEX `idx_v3_submission_id` (`submission_id`),
  INDEX `idx_v3_team_create_time` (`team_id`, `create_time`),
  INDEX `idx_v3_parse_artifact` (`parse_artifact_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='V3 深度证据化评审结果表';
