CREATE TABLE `contest` (
  `id` BIGINT NOT NULL COMMENT '赛事ID',
  `code` VARCHAR(32) NOT NULL COMMENT '赛事编码',
  `name` VARCHAR(100) NOT NULL COMMENT '赛事名称',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0-停用 1-启用',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE INDEX `uk_code` (`code`),
  INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='赛事表';

INSERT INTO `contest` (`id`, `code`, `name`) VALUES
  (1, 'MCM_ICM', '美国大学生数学建模竞赛'),
  (2, 'CUMCM', '全国大学生数学建模竞赛');

ALTER TABLE `problem`
  ADD COLUMN `contest_id` BIGINT NULL COMMENT '赛事ID' AFTER `content_file_id`,
  ADD COLUMN `year` SMALLINT NULL COMMENT '题目年份' AFTER `contest_type`,
  ADD COLUMN `statement_language` VARCHAR(16) NULL COMMENT '题面语言' AFTER `year`,
  ADD COLUMN `duration_minutes` INT NULL COMMENT '完成时长，单位分钟' AFTER `statement_language`;

UPDATE `problem` SET `contest_id` = CASE `contest_type` WHEN 'MCM_ICM' THEN 1 ELSE 2 END,
  `year` = 2026, `statement_language` = 'ZH', `duration_minutes` = 4320;

ALTER TABLE `problem`
  MODIFY COLUMN `contest_id` BIGINT NOT NULL,
  MODIFY COLUMN `year` SMALLINT NOT NULL,
  MODIFY COLUMN `statement_language` VARCHAR(16) NOT NULL,
  MODIFY COLUMN `duration_minutes` INT NOT NULL,
  DROP COLUMN `contest_type`,
  DROP INDEX `idx_contest_difficulty`,
  ADD INDEX `idx_contest_year_difficulty` (`contest_id`, `year`, `difficulty`);
