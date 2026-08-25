CREATE TABLE `team_recruitment`
(
    `id`              BIGINT      NOT NULL COMMENT '招募位置 ID',
    `team_id`         BIGINT      NOT NULL COMMENT '队伍 ID',
    `need_modeler`    TINYINT     NOT NULL DEFAULT 0 COMMENT '需要建模职责',
    `need_programmer` TINYINT     NOT NULL DEFAULT 0 COMMENT '需要编程职责',
    `need_writer`     TINYINT     NOT NULL DEFAULT 0 COMMENT '需要论文职责',
    `status`          VARCHAR(16) NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN FILLED CLOSED',
    `create_time`     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    INDEX `idx_team_status` (`team_id`, `status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='队伍招募位置表';

-- 将仍在组建且开启招募的旧配置迁移为一个独立招募位置。
INSERT INTO `team_recruitment` (`id`, `team_id`, `need_modeler`, `need_programmer`, `need_writer`, `status`)
SELECT `id` + 700000000000000000, `id`, `need_modeler`, `need_programmer`, `need_writer`, 'OPEN'
FROM `team`
WHERE `status` = 1
  AND `practice_status` = 'PREPARING'
  AND `recruiting` = 1
  AND (`need_modeler` = 1 OR `need_programmer` = 1 OR `need_writer` = 1)
  AND (SELECT COUNT(*) FROM `team_member` tm WHERE tm.team_id = `team`.id) < 3;

ALTER TABLE `team_join_application`
    ADD COLUMN `recruitment_id` BIGINT DEFAULT NULL COMMENT '申请的招募位置 ID' AFTER `team_id`;

UPDATE `team_join_application` a
JOIN `team_recruitment` r ON r.team_id = a.team_id
SET a.recruitment_id = r.id
WHERE a.status = 'pending';

-- 无法关联到有效招募位置的历史待处理申请不再具备审核条件。
UPDATE `team_join_application`
SET `status` = 'closed', `pending_marker` = NULL, `handled_at` = CURRENT_TIMESTAMP
WHERE `status` = 'pending' AND `recruitment_id` IS NULL;

ALTER TABLE `team_join_application`
    DROP INDEX `uk_team_application_pending`,
    ADD UNIQUE KEY `uk_team_applicant_pending` (`team_id`, `applicant_id`, `pending_marker`),
    ADD INDEX `idx_recruitment_status` (`recruitment_id`, `status`),
    DROP COLUMN `desired_modeler`,
    DROP COLUMN `desired_programmer`,
    DROP COLUMN `desired_writer`;

ALTER TABLE `team`
    DROP COLUMN `max_members`,
    DROP COLUMN `recruiting`,
    DROP COLUMN `need_modeler`,
    DROP COLUMN `need_programmer`,
    DROP COLUMN `need_writer`;
