CREATE TABLE `submission` (
  `id` BIGINT NOT NULL,
  `team_id` BIGINT NOT NULL,
  `problem_id` BIGINT NOT NULL,
  `submitter_id` BIGINT NOT NULL,
  `version` INT NOT NULL,
  `original_filename` VARCHAR(255) NOT NULL,
  `object_name` VARCHAR(512) NOT NULL,
  `file_size` BIGINT NOT NULL,
  `status` VARCHAR(20) NOT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE INDEX `uk_team_version` (`team_id`, `version`),
  INDEX `idx_team_create_time` (`team_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='论文提交版本';

CREATE TABLE `submission_lock` (
  `id` BIGINT NOT NULL,
  `team_id` BIGINT NOT NULL,
  `submission_id` BIGINT NOT NULL,
  `locked_at` DATETIME NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE INDEX `uk_team_id` (`team_id`),
  UNIQUE INDEX `uk_submission_id` (`submission_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='队伍最终提交锁定';
