ALTER TABLE `team_member`
    ADD COLUMN `modeler` TINYINT NOT NULL DEFAULT 0 COMMENT '是否为建模手：0=否 1=是' AFTER `role`,
    ADD COLUMN `programmer` TINYINT NOT NULL DEFAULT 0 COMMENT '是否为编程手：0=否 1=是' AFTER `modeler`,
    ADD COLUMN `writer` TINYINT NOT NULL DEFAULT 0 COMMENT '是否为论文手：0=否 1=是' AFTER `programmer`;
