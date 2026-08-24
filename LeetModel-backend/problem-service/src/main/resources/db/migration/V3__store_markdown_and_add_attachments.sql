ALTER TABLE `problem`
  ADD COLUMN `content_markdown` LONGTEXT NULL COMMENT 'Markdown题面原文' AFTER `title`,
  DROP COLUMN `content_file_id`;

CREATE TABLE `problem_attachment` (
  `id` BIGINT NOT NULL COMMENT '附件ID',
  `problem_id` BIGINT NOT NULL COMMENT '题目ID',
  `file_name` VARCHAR(255) NOT NULL COMMENT '原始文件名',
  `object_key` VARCHAR(512) NOT NULL COMMENT '对象存储路径',
  `content_type` VARCHAR(100) NOT NULL COMMENT '媒体类型',
  `file_size` BIGINT NOT NULL COMMENT '文件字节数',
  `description` VARCHAR(500) DEFAULT NULL COMMENT '附件说明',
  `sort_order` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '展示顺序',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE INDEX `uk_object_key` (`object_key`),
  INDEX `idx_problem_id` (`problem_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='题目附件表';

DROP TABLE `problem_link`;

DELETE c FROM `contest` c
LEFT JOIN `problem` p ON p.`contest_id` = c.`id` AND p.`deleted` = 0
WHERE c.`id` IN (1, 2)
  AND c.`code` IN ('MCM_ICM', 'CUMCM')
  AND p.`id` IS NULL;
