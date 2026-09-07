-- 将题目题号统一调整为从 1 起始自增，标识归标识（id 雪花主键），题号归题号（code 业务自然题号）
UPDATE `problem` p
JOIN (
    SELECT id, ROW_NUMBER() OVER (ORDER BY id) AS seq
    FROM `problem`
) r ON p.id = r.id
SET p.code = r.seq;

ALTER TABLE `problem`
    MODIFY COLUMN `code` INT NOT NULL COMMENT '题号（从 1 起始按顺序自增）';
