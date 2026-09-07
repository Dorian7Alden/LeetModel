-- 为演示题目 51001（城市共享单车潮汐调度）补充「评价」题型
-- 形成包含单题型、双题型、三题型（预测 + 评价 + 优化）的完整测试样例梯度

INSERT INTO `problem_tag` (`id`, `problem_id`, `tag_id`) VALUES
(53106, 51001, 6102)
ON DUPLICATE KEY UPDATE `tag_id` = VALUES(`tag_id`);
