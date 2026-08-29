ALTER TABLE `evaluation_task`
  ADD COLUMN `raw_metrics_json` LONGTEXT NULL AFTER `metric_definition_snapshot_json`;
