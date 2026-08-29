ALTER TABLE `ai_call_task`
  ADD COLUMN `model_execution_config_snapshot` JSON NULL
    AFTER `model_execution_config_version`;

UPDATE `ai_call_task`
   SET `model_execution_config_snapshot` = JSON_OBJECT(
           'modelExecutionConfigVersion', COALESCE(`model_execution_config_version`, 'LEGACY_UNSPECIFIED'),
           'callType', `call_type`,
           'legacyBackfill', TRUE)
 WHERE `model_execution_config_snapshot` IS NULL;

ALTER TABLE `ai_call_task`
  MODIFY COLUMN `model_execution_config_snapshot` JSON NOT NULL;
