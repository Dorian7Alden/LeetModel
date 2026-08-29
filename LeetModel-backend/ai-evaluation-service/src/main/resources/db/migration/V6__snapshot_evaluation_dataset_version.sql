ALTER TABLE `evaluation_task`
  ADD COLUMN `dataset_version` VARCHAR(64) NULL AFTER `dataset_id`;

UPDATE `evaluation_task` task
JOIN `evaluation_dataset` dataset ON dataset.`id` = task.`dataset_id`
SET task.`dataset_version` = dataset.`dataset_version`
WHERE task.`dataset_version` IS NULL;

ALTER TABLE `evaluation_task`
  MODIFY COLUMN `dataset_version` VARCHAR(64) NOT NULL,
  ADD INDEX `idx_comparison_criteria` (`feature_code`, `dataset_version`,
                                      `metric_set_version`, `model_execution_config_version`);
