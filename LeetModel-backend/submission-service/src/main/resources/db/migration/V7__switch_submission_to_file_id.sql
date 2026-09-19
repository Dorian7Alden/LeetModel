-- 正式论文改用 file-service 稳定 fileId，不再保存对象存储路径。
-- 历史行按 object_name 关联 lm_file.file_asset 回填；历史演示数据中不存在物理对象的记录保持 file_id 为空。
ALTER TABLE `submission`
  ADD COLUMN `file_id` BIGINT NULL COMMENT '正式论文文件资产ID（file-service）' AFTER `original_filename`;

SET @lm_file_exists = (
  SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name = 'lm_file'
);
SET @backfill_sql = IF(@lm_file_exists > 0,
  'UPDATE submission s JOIN lm_file.file_asset f ON f.object_key COLLATE utf8mb4_unicode_ci = s.object_name COLLATE utf8mb4_unicode_ci SET s.file_id = f.id WHERE s.object_name IS NOT NULL AND s.file_id IS NULL',
  'SELECT 1');
PREPARE backfill_stmt FROM @backfill_sql;
EXECUTE backfill_stmt;
DEALLOCATE PREPARE backfill_stmt;

ALTER TABLE `submission`
  DROP COLUMN `object_name`,
  ADD INDEX `idx_submission_file` (`file_id`);
