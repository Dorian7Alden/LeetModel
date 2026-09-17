-- 为现有测试数据稳定分配 A-F 题号。
-- 使用主键计算伪随机结果，保证不同环境重复执行时结果一致；单值字段保证每道题只有一个题号。
UPDATE `problem`
SET `problem_number` = ELT(
        MOD(CRC32(CAST(`id` AS CHAR)), 6) + 1,
        'A', 'B', 'C', 'D', 'E', 'F'
    )
WHERE `deleted` = 0
  AND `problem_number` = 'OTHER';
