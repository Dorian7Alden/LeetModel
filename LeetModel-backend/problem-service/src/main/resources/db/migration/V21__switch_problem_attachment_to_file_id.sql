-- 题目附件改用 file-service 稳定 fileId，不再保存对象存储路径。
-- 项目尚未上线，不保留兼容窗口；历史行按 object_key 关联 lm_file.file_asset 回填。
-- 若存在无法匹配的历史行，本迁移会在收紧 file_id 时失败，而不是静默丢弃对象引用。
ALTER TABLE `problem_attachment`
  ADD COLUMN `file_id` BIGINT NULL COMMENT '文件资产ID（file-service）' AFTER `problem_id`;

SET @lm_file_exists = (
  SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name = 'lm_file'
);
SET @backfill_sql = IF(@lm_file_exists > 0,
  'UPDATE problem_attachment a JOIN lm_file.file_asset f ON f.object_key COLLATE utf8mb4_unicode_ci = a.object_key COLLATE utf8mb4_unicode_ci SET a.file_id = f.id WHERE a.file_id IS NULL',
  'SELECT 1');
PREPARE backfill_stmt FROM @backfill_sql;
EXECUTE backfill_stmt;
DEALLOCATE PREPARE backfill_stmt;

ALTER TABLE `problem_attachment`
  DROP INDEX `uk_object_key`,
  DROP COLUMN `object_key`,
  MODIFY COLUMN `file_id` BIGINT NOT NULL COMMENT '文件资产ID（file-service）',
  ADD INDEX `idx_problem_attachment_file` (`file_id`);
