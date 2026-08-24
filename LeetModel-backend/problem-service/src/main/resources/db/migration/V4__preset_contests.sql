INSERT INTO `contest` (`id`, `code`, `name`, `status`, `deleted`) VALUES
  (1, 'MCM_ICM', '美国大学生数学建模竞赛', 1, 0),
  (2, 'CUMCM', '全国大学生数学建模竞赛', 1, 0),
  (3, 'LM', '力模', 1, 0)
ON DUPLICATE KEY UPDATE
  `name` = VALUES(`name`),
  `status` = 1,
  `deleted` = 0;

ALTER TABLE `contest`
  DROP INDEX `idx_status`,
  DROP COLUMN `status`;
