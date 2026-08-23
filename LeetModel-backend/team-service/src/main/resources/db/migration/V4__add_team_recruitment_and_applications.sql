ALTER TABLE `team`
    ADD COLUMN `recruiting` TINYINT NOT NULL DEFAULT 1 COMMENT '是否正在招募：0=否 1=是' AFTER `status`,
    ADD COLUMN `need_modeler` TINYINT NOT NULL DEFAULT 0 COMMENT '是否招募建模手：0=否 1=是' AFTER `recruiting`,
    ADD COLUMN `need_programmer` TINYINT NOT NULL DEFAULT 0 COMMENT '是否招募编程手：0=否 1=是' AFTER `need_modeler`,
    ADD COLUMN `need_writer` TINYINT NOT NULL DEFAULT 0 COMMENT '是否招募论文手：0=否 1=是' AFTER `need_programmer`;

UPDATE `team` SET `recruiting` = 0 WHERE `status` = 0;

CREATE TABLE IF NOT EXISTS `team_join_application`
(
    `id`                 BIGINT       NOT NULL COMMENT '申请 ID',
    `team_id`            BIGINT       NOT NULL COMMENT '目标团队 ID',
    `applicant_id`       BIGINT       NOT NULL COMMENT '申请人用户 ID',
    `desired_modeler`    TINYINT      NOT NULL DEFAULT 0 COMMENT '希望担任建模手',
    `desired_programmer` TINYINT      NOT NULL DEFAULT 0 COMMENT '希望担任编程手',
    `desired_writer`     TINYINT      NOT NULL DEFAULT 0 COMMENT '希望担任论文手',
    `message`            VARCHAR(256) DEFAULT NULL COMMENT '申请说明',
    `status`             VARCHAR(16)  NOT NULL COMMENT 'pending approved rejected cancelled closed',
    `pending_marker`     TINYINT      DEFAULT NULL COMMENT '待处理唯一标记，待处理时为 1',
    `handled_by`         BIGINT       DEFAULT NULL COMMENT '审核人用户 ID',
    `handled_at`         DATETIME     DEFAULT NULL COMMENT '处理时间',
    `create_time`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '申请时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_team_application_pending` (`team_id`, `applicant_id`, `pending_marker`),
    INDEX `idx_team_status` (`team_id`, `status`),
    INDEX `idx_applicant_status` (`applicant_id`, `status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='入队申请表';
