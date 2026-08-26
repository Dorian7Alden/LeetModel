-- 题目量非常有限（通常 <= 10000，每年全部赛事新增不超过 100），
-- 题号改为短顺序编号，避免暴露内部雪花主键导致展示过长。
ALTER TABLE `problem` ADD COLUMN `code` INT NULL COMMENT '题号（短顺序编号，从 1001 起始）' AFTER `id`;

UPDATE `problem` p
JOIN (
    SELECT id, 1000 + ROW_NUMBER() OVER (ORDER BY id) AS seq
    FROM `problem`
) r ON p.id = r.id
SET p.code = r.seq;

ALTER TABLE `problem`
    MODIFY COLUMN `code` INT NOT NULL COMMENT '题号（短顺序编号，从 1001 起始）',
    ADD UNIQUE KEY `uk_problem_code` (`code`);
