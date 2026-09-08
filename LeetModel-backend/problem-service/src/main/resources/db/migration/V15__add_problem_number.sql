-- 赛事内题号分类：标准赛事题号为 A-F，无法归类的历史或自定义赛题使用 OTHER。
ALTER TABLE `problem`
    ADD COLUMN `problem_number` VARCHAR(5) NOT NULL DEFAULT 'OTHER'
        COMMENT '赛事内题号：A、B、C、D、E、F 或 OTHER'
        AFTER `code`,
    ADD INDEX `idx_contest_problem_number` (`contest_id`, `problem_number`);

-- 演示数据按赛事题号补齐，未明确归类的题目保留 OTHER。
UPDATE `problem`
SET `problem_number` = CASE `id`
    WHEN 51001 THEN 'A'
    WHEN 51002 THEN 'B'
    WHEN 51003 THEN 'C'
    WHEN 51004 THEN 'A'
    WHEN 51005 THEN 'B'
    WHEN 51006 THEN 'C'
    WHEN 51007 THEN 'A'
    WHEN 51008 THEN 'B'
    ELSE 'OTHER'
END
WHERE `id` IN (51001, 51002, 51003, 51004, 51005, 51006, 51007, 51008, 51009);
