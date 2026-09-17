ALTER TABLE `assistant_workflow_version`
  ADD COLUMN `toolset_version` VARCHAR(64) NULL AFTER `model_execution_config_version`;

ALTER TABLE `assistant_production_config`
  ADD COLUMN `toolset_version` VARCHAR(64) NULL AFTER `model_execution_config_version`;

ALTER TABLE `assistant_message`
  ADD COLUMN `toolset_version` VARCHAR(64) NULL AFTER `model_execution_config_version`,
  ADD COLUMN `attempt_count` INT NOT NULL DEFAULT 0 AFTER `toolset_version`;

INSERT INTO `assistant_workflow_version`
(`id`, `workflow_version`, `name`, `status`, `prompt_version`,
 `model_execution_config_version`, `toolset_version`, `rag_mode`,
 `input_schema`, `output_schema`, `compatibility`, `impact_scope`, `experiment_candidate`)
VALUES
(3, 'ASSISTANT_TOOLS_NO_RAG_V1', '客服工具版无RAG V1', 'ENABLED',
 'PROMPT_ASSISTANT_TOOLS_0001', 'MODEL_CFG_ASSISTANT_TOOLS_0001',
 'ASSISTANT_TOOLSET_0001', 'NONE', 'ASSISTANT_QUESTION_V1', 'ASSISTANT_REPLY_V1',
 '使用固定三工具集；旧V1消息、配置和回复继续按无工具快照读取',
 '仅影响激活后新创建的客服回复；在途与历史消息不变', 1),
(4, 'ASSISTANT_TOOLS_RAG_V1', '客服工具版固定索引RAG V1', 'ENABLED',
 'PROMPT_ASSISTANT_TOOLS_0001', 'MODEL_CFG_ASSISTANT_TOOLS_0001',
 'ASSISTANT_TOOLSET_0001', 'FIXED_INDEX', 'ASSISTANT_QUESTION_V1', 'ASSISTANT_REPLY_V1',
 '使用固定三工具集且必须绑定可用物理RAG索引；旧V1消息、配置和回复继续按原快照读取',
 '仅影响激活后新创建的客服回复及其固定RAG检索；在途与历史消息不变', 1);

CREATE INDEX `idx_toolset_version`
  ON `assistant_message` (`toolset_version`);
