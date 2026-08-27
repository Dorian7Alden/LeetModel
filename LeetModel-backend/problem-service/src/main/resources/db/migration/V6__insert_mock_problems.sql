-- 由 LeetModel-mock/scripts/generate_problem_service_demo.py 生成
-- 覆盖赛事、年份、语言、难度、三类标签组合、分数及草稿隔离场景

INSERT INTO `tag` (`id`, `name`, `type`) VALUES
(6001, '环境生态', 'BACKGROUND_DOMAIN'),
(6002, '交通物流', 'BACKGROUND_DOMAIN'),
(6003, '经济金融', 'BACKGROUND_DOMAIN'),
(6004, '公共健康', 'BACKGROUND_DOMAIN'),
(6101, '预测', 'PROBLEM_TYPE'),
(6102, '评价', 'PROBLEM_TYPE'),
(6103, '优化', 'PROBLEM_TYPE'),
(6201, '回归分析', 'MODEL_ALGORITHM'),
(6202, '层次分析法', 'MODEL_ALGORITHM'),
(6203, '线性规划', 'MODEL_ALGORITHM'),
(6204, '蒙特卡洛模拟', 'MODEL_ALGORITHM')
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`), `type` = VALUES(`type`);

INSERT INTO `problem` (`id`, `title`, `content_markdown`, `contest_id`, `year`, `statement_language`, `duration_minutes`, `difficulty`, `average_score`, `status`, `creator_id`, `deleted`) VALUES
(51001, '城市共享单车潮汐调度', '# 城市共享单车潮汐调度

建立需求预测与车辆调度模型，降低高峰期供需失衡。', 2, 2024, 'ZH', 4320, 2, 86.50, 1, 1, 0),
(51002, '湖泊水质变化预测', '# 湖泊水质变化预测

根据监测数据预测主要水质指标，并分析关键影响因素。', 2, 2023, 'ZH', 4320, 2, 78.20, 1, 1, 0),
(51003, '社区医疗资源评价', '# 社区医疗资源评价

构建社区医疗服务能力评价体系。', 2, 2022, 'ZH', 4320, 1, 72.00, 1, 1, 0),
(51004, 'Urban Delivery Route Planning', '# Urban Delivery Route Planning

Design a robust routing plan under uncertain demand.', 1, 2024, 'EN', 4320, 3, 91.30, 1, 1, 0),
(51005, 'Carbon Market Price Forecasting', '# Carbon Market Price Forecasting

Forecast carbon allowance prices and quantify uncertainty.', 1, 2023, 'EN', 4320, 3, 88.80, 1, 1, 0),
(51006, '生态保护区承载力评价', '# 生态保护区承载力评价

评价旅游活动对生态保护区承载力的影响。', 3, 2026, 'ZH', 4320, 2, 82.40, 1, 1, 0),
(51007, '应急物资配送优化', '# 应急物资配送优化

在道路通行能力动态变化时制定配送方案。', 3, 2026, 'ZH', 4320, 3, 93.10, 1, 1, 0),
(51008, '流感就诊人数预测', '# 流感就诊人数预测

根据历史就诊与气象数据预测短期就诊需求。', 3, 2025, 'ZH', 4320, 1, 76.60, 1, 1, 0),
(51009, '未发布的金融风险评价', '# 未发布测试题

用于验证公开题库不会返回草稿。', 3, 2026, 'ZH', 4320, 2, 80.00, 0, 1, 0)
ON DUPLICATE KEY UPDATE `title` = VALUES(`title`), `content_markdown` = VALUES(`content_markdown`), `contest_id` = VALUES(`contest_id`), `year` = VALUES(`year`), `statement_language` = VALUES(`statement_language`), `duration_minutes` = VALUES(`duration_minutes`), `difficulty` = VALUES(`difficulty`), `average_score` = VALUES(`average_score`), `status` = VALUES(`status`), `deleted` = 0;

INSERT INTO `problem_tag` (`id`, `problem_id`, `tag_id`) VALUES
(53001, 51001, 6002),
(53002, 51001, 6103),
(53003, 51001, 6203),
(53004, 51002, 6001),
(53005, 51002, 6101),
(53006, 51002, 6201),
(53007, 51003, 6004),
(53008, 51003, 6102),
(53009, 51003, 6202),
(53010, 51004, 6002),
(53011, 51004, 6103),
(53012, 51004, 6204),
(53013, 51005, 6003),
(53014, 51005, 6101),
(53015, 51005, 6201),
(53016, 51006, 6001),
(53017, 51006, 6102),
(53018, 51006, 6202),
(53019, 51007, 6002),
(53020, 51007, 6103),
(53021, 51007, 6203),
(53022, 51008, 6004),
(53023, 51008, 6101),
(53024, 51008, 6201),
(53025, 51009, 6003),
(53026, 51009, 6102),
(53027, 51009, 6202)
ON DUPLICATE KEY UPDATE `tag_id` = VALUES(`tag_id`);
