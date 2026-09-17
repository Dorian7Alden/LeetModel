CREATE TABLE `paper_parse_chunk_artifact` (
  `id` BIGINT NOT NULL,
  `submission_id` BIGINT NOT NULL,
  `workflow_version` VARCHAR(40) NOT NULL,
  `window_index` INT NOT NULL,
  `start_page` INT NOT NULL,
  `end_page` INT NOT NULL,
  `status` VARCHAR(20) NOT NULL,
  `chunk_json` JSON NULL,
  `attempt_no` INT NOT NULL DEFAULT 1,
  `error_message` VARCHAR(500) NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE INDEX `uk_submission_window` (`submission_id`, `workflow_version`, `window_index`),
  INDEX `idx_chunk_submission` (`submission_id`, `workflow_version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='不可变 PDF 解析滑窗中间分块产物';
