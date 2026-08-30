CREATE TABLE `assistant_workflow_version` (
  `id` BIGINT NOT NULL,
  `workflow_version` VARCHAR(64) NOT NULL,
  `name` VARCHAR(100) NOT NULL,
  `status` VARCHAR(20) NOT NULL,
  `prompt_version` VARCHAR(64) NOT NULL,
  `model_execution_config_version` VARCHAR(64) NOT NULL,
  `rag_mode` VARCHAR(20) NOT NULL,
  `input_schema` VARCHAR(64) NOT NULL,
  `output_schema` VARCHAR(64) NOT NULL,
  `compatibility` VARCHAR(500) NOT NULL,
  `impact_scope` VARCHAR(500) NOT NULL,
  `experiment_candidate` TINYINT NOT NULL DEFAULT 1,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE INDEX `uk_workflow_version` (`workflow_version`),
  INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI客服不可变工作流发布目录';

CREATE TABLE `assistant_production_config` (
  `id` BIGINT NOT NULL,
  `production_config_version` VARCHAR(64) NOT NULL,
  `workflow_version` VARCHAR(64) NOT NULL,
  `prompt_version` VARCHAR(64) NOT NULL,
  `model_execution_config_version` VARCHAR(64) NOT NULL,
  `rag_mode` VARCHAR(20) NOT NULL,
  `rag_index_version` VARCHAR(128) NULL,
  `rag_index_key` VARCHAR(128) NOT NULL,
  `created_by` BIGINT NOT NULL,
  `reason` VARCHAR(500) NOT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE INDEX `uk_production_config_version` (`production_config_version`),
  UNIQUE INDEX `uk_workflow_rag_index` (`workflow_version`, `rag_index_key`),
  INDEX `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI客服不可变生产配置';

CREATE TABLE `assistant_production_pointer` (
  `id` BIGINT NOT NULL,
  `active_config_id` BIGINT NOT NULL,
  `revision` BIGINT NOT NULL,
  `activated_by` BIGINT NOT NULL,
  `activated_at` DATETIME NOT NULL,
  `observation_until` DATETIME NOT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI客服唯一生产配置指针';

CREATE TABLE `assistant_production_change_request` (
  `id` BIGINT NOT NULL,
  `change_request_id` VARCHAR(32) NOT NULL,
  `action` VARCHAR(20) NOT NULL,
  `expected_revision` BIGINT NOT NULL,
  `source_config_id` BIGINT NOT NULL,
  `target_config_id` BIGINT NOT NULL,
  `operator_id` BIGINT NOT NULL,
  `reason` VARCHAR(500) NOT NULL,
  `status` VARCHAR(20) NOT NULL,
  `expires_at` DATETIME NOT NULL,
  `applied_at` DATETIME NULL,
  `result_message` VARCHAR(200) NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE INDEX `uk_change_request_id` (`change_request_id`),
  INDEX `idx_operator_status` (`operator_id`, `status`),
  INDEX `idx_expires_at` (`expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI客服生产配置二次确认请求';

CREATE TABLE `assistant_production_audit` (
  `id` BIGINT NOT NULL,
  `change_request_id` VARCHAR(32) NOT NULL,
  `action` VARCHAR(20) NOT NULL,
  `from_config_id` BIGINT NOT NULL,
  `to_config_id` BIGINT NOT NULL,
  `from_revision` BIGINT NOT NULL,
  `to_revision` BIGINT NOT NULL,
  `operator_id` BIGINT NOT NULL,
  `reason` VARCHAR(500) NOT NULL,
  `changed_at` DATETIME NOT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE INDEX `uk_audit_change_request` (`change_request_id`),
  INDEX `idx_changed_at` (`changed_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI客服生产配置生效审计';

INSERT INTO `assistant_workflow_version`
(`id`, `workflow_version`, `name`, `status`, `prompt_version`,
 `model_execution_config_version`, `rag_mode`, `input_schema`, `output_schema`,
 `compatibility`, `impact_scope`, `experiment_candidate`)
VALUES
(1, 'ASSISTANT_NO_RAG_V1', '客服无RAG V1', 'ENABLED', 'PROMPT_ASSISTANT_CHAT_0001',
 'MODEL_CFG_ASSISTANT_TEXT_0001', 'NONE', 'ASSISTANT_QUESTION_V1', 'ASSISTANT_REPLY_V1',
 '不读取知识索引；历史回答和实验结果继续按原快照读取', '新创建的客服回复；不影响在途与历史消息', 1),
(2, 'ASSISTANT_RAG_V1', '客服固定索引RAG V1', 'ENABLED', 'PROMPT_ASSISTANT_CHAT_0001',
 'MODEL_CFG_ASSISTANT_TEXT_0001', 'FIXED_INDEX', 'ASSISTANT_QUESTION_V1', 'ASSISTANT_REPLY_V1',
 '激活时必须绑定可查询的物理ragIndexVersion；检索失败不切换配置',
 '新创建的客服回复及其RAG检索；不影响在途与历史消息', 1);

INSERT INTO `assistant_production_config`
(`id`, `production_config_version`, `workflow_version`, `prompt_version`,
 `model_execution_config_version`, `rag_mode`, `rag_index_version`, `rag_index_key`,
 `created_by`, `reason`)
VALUES
(1, 'ASSISTANT_PROD_CFG_0001', 'ASSISTANT_NO_RAG_V1', 'PROMPT_ASSISTANT_CHAT_0001',
 'MODEL_CFG_ASSISTANT_TEXT_0001', 'NONE', NULL, 'NONE', 0, '系统安全默认配置');

INSERT INTO `assistant_production_pointer`
(`id`, `active_config_id`, `revision`, `activated_by`, `activated_at`, `observation_until`)
VALUES (1, 1, 1, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

ALTER TABLE `assistant_message`
  ADD COLUMN `production_config_version` VARCHAR(64) NULL AFTER `status`,
  ADD COLUMN `production_revision` BIGINT NULL AFTER `production_config_version`,
  ADD COLUMN `workflow_version` VARCHAR(64) NULL AFTER `production_revision`,
  ADD COLUMN `prompt_version` VARCHAR(64) NULL AFTER `workflow_version`,
  ADD COLUMN `model_execution_config_version` VARCHAR(64) NULL AFTER `prompt_version`,
  ADD COLUMN `rag_mode` VARCHAR(20) NULL AFTER `model_execution_config_version`,
  ADD COLUMN `rag_index_version` VARCHAR(128) NULL AFTER `rag_mode`;

UPDATE `assistant_message`
SET `production_config_version` = 'ASSISTANT_PROD_CFG_0001',
    `production_revision` = 1,
    `workflow_version` = 'ASSISTANT_NO_RAG_V1',
    `prompt_version` = 'PROMPT_ASSISTANT_CHAT_0001',
    `model_execution_config_version` = 'MODEL_CFG_ASSISTANT_TEXT_0001',
    `rag_mode` = 'NONE'
WHERE `role` = 'ASSISTANT' AND `production_config_version` IS NULL;

CREATE INDEX `idx_production_config_version`
  ON `assistant_message` (`production_config_version`);
