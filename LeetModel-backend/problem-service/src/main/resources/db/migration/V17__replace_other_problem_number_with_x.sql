-- 将无法归入 A-F 的赛事题号统一为 X，并收紧字段长度与默认值。
UPDATE `problem`
SET `problem_number` = 'X'
WHERE `problem_number` = 'OTHER';

ALTER TABLE `problem`
    MODIFY COLUMN `problem_number` CHAR(1) NOT NULL DEFAULT 'X'
        COMMENT '赛事内题号：A、B、C、D、E、F 或 X';
