-- 用户头像改用 file-service 稳定 fileId；外部绝对 URL 保留在独立列，仅用于演示与遗留数据。
-- 项目尚未上线，不保留兼容窗口；平台内对象路径按 object_key 关联 lm_file.file_asset 回填。
ALTER TABLE `user`
  ADD COLUMN `avatar_file_id` BIGINT NULL COMMENT '当前头像文件资产ID' AFTER `email`,
  ADD COLUMN `avatar_url` VARCHAR(2048) NULL COMMENT '外部头像绝对URL（演示/遗留）' AFTER `avatar_file_id`;

UPDATE `user` SET `avatar_url` = `avatar_path`
WHERE `avatar_path` LIKE 'http://%' OR `avatar_path` LIKE 'https://%';

SET @lm_file_exists = (
  SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name = 'lm_file'
);
SET @backfill_sql = IF(@lm_file_exists > 0,
  'UPDATE `user` u JOIN lm_file.file_asset f ON f.object_key COLLATE utf8mb4_unicode_ci = u.avatar_path COLLATE utf8mb4_unicode_ci SET u.avatar_file_id = f.id WHERE u.avatar_path IS NOT NULL AND u.avatar_path NOT LIKE ''http://%'' AND u.avatar_path NOT LIKE ''https://%''',
  'SELECT 1');
PREPARE backfill_stmt FROM @backfill_sql;
EXECUTE backfill_stmt;
DEALLOCATE PREPARE backfill_stmt;

ALTER TABLE `user`
  DROP COLUMN `avatar_path`;
