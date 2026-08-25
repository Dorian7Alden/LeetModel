ALTER TABLE `team_member`
    ADD COLUMN `can_submit` TINYINT NOT NULL DEFAULT 0 COMMENT '是否允许提交作品：0=否 1=是' AFTER `writer`;

UPDATE `team_member` SET `can_submit` = 1 WHERE `role` = 'leader';

UPDATE `team`
SET `practice_status` = 'ENDED',
    `ended_at` = COALESCE(`ended_at`, `deadline_at`, `update_time`)
WHERE `practice_status` IN ('SUBMITTED', 'COMPLETED');

UPDATE `team`
SET `practice_status` = 'DISBANDED',
    `ended_at` = COALESCE(`ended_at`, `update_time`),
    `recruiting` = 0
WHERE `status` = 0;
