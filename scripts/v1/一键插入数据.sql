-- =============================================
-- 1. 用户表数据（20条，含学生、管理员）
-- =============================================
INSERT INTO `user` VALUES
(1, '张建模', 'zhang@edu.cn', '$2a$10$fakehash1', '清华大学', '13800138001', 'user', 'modeler', 'normal', NOW(), NOW()),
(2, '李编程', 'li@edu.cn', '$2a$10$fakehash2', '北京大学', '13800138002', 'user', 'coder', 'normal', NOW(), NOW()),
(3, '王论文', 'wang@edu.cn', '$2a$10$fakehash3', '浙江大学', '13800138003', 'user', 'writer', 'normal', NOW(), NOW()),
(4, '赵同学', 'zhao@edu.cn', '$2a$10$fakehash4', '复旦大学', '13800138004', 'user', 'modeler', 'normal', NOW(), NOW()),
(5, '钱选手', 'qian@edu.cn', '$2a$10$fakehash5', '上海交通大学', '13800138005', 'user', 'coder', 'normal', NOW(), NOW()),
(6, '孙学霸', 'sun@edu.cn', '$2a$10$fakehash6', '南京大学', '13800138006', 'user', 'writer', 'normal', NOW(), NOW()),
(7, '周队长', 'zhou@edu.cn', '$2a$10$fakehash7', '中山大学', '13800138007', 'user', 'modeler', 'normal', NOW(), NOW()),
(8, '吴队员', 'wu@edu.cn', '$2a$10$fakehash8', '华中科技大学', '13800138008', 'user', 'coder', 'normal', NOW(), NOW()),
(9, '郑写手', 'zheng@edu.cn', '$2a$10$fakehash9', '西安交通大学', '13800138009', 'user', 'writer', 'normal', NOW(), NOW()),
(10, '刘萌新', 'liu@edu.cn', '$2a$10$fakehash10', '哈尔滨工业大学', '13800138010', 'user', 'modeler', 'normal', NOW(), NOW()),
(11, '陈管理员', 'chen@leetmodel.com', '$2a$10$adminhash', '平台运营', '13900139001', 'admin', NULL, 'normal', NOW(), NOW()),
(12, '杨教练', 'yang@edu.cn', '$2a$10$fakehash12', '清华大学', '13800138012', 'user', 'modeler', 'normal', NOW(), NOW()),
(13, '黄参赛', 'huang@edu.cn', '$2a$10$fakehash13', '北京大学', '13800138013', 'user', 'coder', 'normal', NOW(), NOW()),
(14, '周备赛', 'zhou2@edu.cn', '$2a$10$fakehash14', '浙江大学', '13800138014', 'user', 'writer', 'normal', NOW(), NOW()),
(15, '吴刷题', 'wu2@edu.cn', '$2a$10$fakehash15', '复旦大学', '13800138015', 'user', 'modeler', 'normal', NOW(), NOW()),
(16, '徐练手', 'xu@edu.cn', '$2a$10$fakehash16', '上海交通大学', '13800138016', 'user', 'coder', 'normal', NOW(), NOW()),
(17, '孙冲刺', 'sun2@edu.cn', '$2a$10$fakehash17', '南京大学', '13800138017', 'user', 'writer', 'normal', NOW(), NOW()),
(18, '马队长', 'ma@edu.cn', '$2a$10$fakehash18', '中山大学', '13800138018', 'user', 'modeler', 'normal', NOW(), NOW()),
(19, '朱队员', 'zhu@edu.cn', '$2a$10$fakehash19', '华中科技大学', '13800138019', 'user', 'coder', 'normal', NOW(), NOW()),
(20, '林写手', 'lin@edu.cn', '$2a$10$fakehash20', '西安交通大学', '13800138020', 'user', 'writer', 'normal', NOW(), NOW());

-- =============================================
-- 2. 标签表数据（30条，覆盖所有类型）
-- =============================================
INSERT INTO `tag` VALUES
(1, '国赛', 'competition', 1, NOW(), NOW()),
(2, '美赛', 'competition', 2, NOW(), NOW()),
(3, '评价模型', 'model', 1, NOW(), NOW()),
(4, '预测模型', 'model', 2, NOW(), NOW()),
(5, '动态规划', 'model', 3, NOW(), NOW()),
(6, '数据分析', 'model', 4, NOW(), NOW()),
(7, '连续问题', 'problem', 1, NOW(), NOW()),
(8, '离散问题', 'problem', 2, NOW(), NOW()),
(9, '太空', 'background', 1, NOW(), NOW()),
(10, '环境保护', 'background', 2, NOW(), NOW()),
(11, '全球气温', 'background', 3, NOW(), NOW()),
(12, '交通优化', 'background', 4, NOW(), NOW()),
(13, '医疗资源', 'background', 5, NOW(), NOW()),
(14, '经验分享', 'post', 1, NOW(), NOW()),
(15, '问题讨论', 'post', 2, NOW(), NOW()),
(16, '技巧干货', 'post', 3, NOW(), NOW()),
(17, '层次分析法', 'model', 5, NOW(), NOW()),
(18, '神经网络', 'model', 6, NOW(), NOW()),
(19, '遗传算法', 'model', 7, NOW(), NOW()),
(20, '模拟退火', 'model', 8, NOW(), NOW()),
(21, '时间序列', 'model', 9, NOW(), NOW()),
(22, '回归分析', 'model', 10, NOW(), NOW()),
(23, '优化问题', 'problem', 3, NOW(), NOW()),
(24, '分类问题', 'problem', 4, NOW(), NOW()),
(25, '经济预测', 'background', 6, NOW(), NOW()),
(26, '社会网络', 'background', 7, NOW(), NOW()),
(27, '图像处理', 'background', 8, NOW(), NOW()),
(28, '文本挖掘', 'background', 9, NOW(), NOW()),
(29, '数模入门', 'post', 4, NOW(), NOW()),
(30, '获奖心得', 'post', 5, NOW(), NOW());

-- =============================================
-- 3. 题目表数据（15条，覆盖所有来源、难度、状态）
-- =============================================
INSERT INTO `problem` VALUES
(1, '城市公共交通系统优化设计', '请针对某城市的公交网络进行优化，目标是减少乘客平均等待时间和运营成本...', 'hard', 'history_raw', 'published', 'CN', 'https://cdn.leetmodel.com/data/problem1.zip', 11, NOW(), NOW()),
(2, '全球气温变化趋势预测', '基于过去50年的气温数据，建立数学模型预测未来20年的全球平均气温变化...', 'medium', 'ai_gen', 'published', 'EN', 'https://cdn.leetmodel.com/data/problem2.zip', 11, NOW(), NOW()),
(3, '医疗资源分配优化', '针对某地区的医疗资源分布情况，建立模型优化医院选址和资源配置...', 'medium', 'history_raw', 'published', 'CN', 'https://cdn.leetmodel.com/data/problem3.zip', 11, NOW(), NOW()),
(4, '太空探测器轨道设计', '设计一个从地球到火星的探测器轨道，考虑燃料消耗和飞行时间...', 'hard', 'ai_gen', 'pending', 'EN', 'https://cdn.leetmodel.com/data/problem4.zip', 1, NOW(), NOW()),
(5, '电商商品推荐系统', '基于用户行为数据，建立商品推荐模型，提高用户购买转化率...', 'easy', 'user_upload', 'published', 'CN', 'https://cdn.leetmodel.com/data/problem5.zip', 2, NOW(), NOW()),
(6, '环境污染扩散模型', '建立数学模型模拟某工厂污染物在河流中的扩散过程...', 'medium', 'history_raw', 'published', 'CN', 'https://cdn.leetmodel.com/data/problem6.zip', 11, NOW(), NOW()),
(7, '股票价格预测', '基于历史股票数据，建立模型预测未来股价走势...', 'hard', 'ai_gen', 'published', 'EN', 'https://cdn.leetmodel.com/data/problem7.zip', 11, NOW(), NOW()),
(8, '校园快递柜布局优化', '针对大学校园的快递需求，优化快递柜的布局位置...', 'easy', 'user_upload', 'published', 'CN', 'https://cdn.leetmodel.com/data/problem8.zip', 3, NOW(), NOW()),
(9, '社交网络影响力分析', '分析社交网络中用户的影响力，识别关键节点...', 'medium', 'history_raw', 'offline', 'EN', 'https://cdn.leetmodel.com/data/problem9.zip', 11, NOW(), NOW()),
(10, '自动驾驶路径规划', '设计自动驾驶车辆的路径规划算法，考虑实时交通状况...', 'hard', 'ai_gen', 'unreviewed', 'CN', 'https://cdn.leetmodel.com/data/problem10.zip', 4, NOW(), NOW()),
(11, '旅游路线优化', '为游客设计最优的旅游路线，最大化景点覆盖和最小化时间...', 'easy', 'history_raw', 'published', 'CN', 'https://cdn.leetmodel.com/data/problem11.zip', 11, NOW(), NOW()),
(12, '图像识别算法优化', '优化图像识别算法，提高识别准确率和速度...', 'hard', 'user_upload', 'rejected', 'EN', 'https://cdn.leetmodel.com/data/problem12.zip', 5, NOW(), NOW()),
(13, '人口增长预测', '建立人口增长模型，预测某地区未来30年的人口变化...', 'medium', 'ai_gen', 'published', 'CN', 'https://cdn.leetmodel.com/data/problem13.zip', 11, NOW(), NOW()),
(14, '能源调度优化', '优化电力系统的能源调度，降低发电成本和碳排放...', 'hard', 'history_raw', 'published', 'EN', 'https://cdn.leetmodel.com/data/problem14.zip', 11, NOW(), NOW()),
(15, '文本情感分析', '建立模型分析文本的情感倾向（正面/负面/中性）...', 'medium', 'user_upload', 'pending', 'CN', 'https://cdn.leetmodel.com/data/problem15.zip', 6, NOW(), NOW());

-- =============================================
-- 4. 题目-标签关联表数据（40条，随机组合）
-- =============================================
INSERT INTO `problem_tag` VALUES
(1, 1, 1, NOW()), (2, 1, 7, NOW()), (3, 1, 12, NOW()), (4, 1, 3, NOW()),
(5, 2, 2, NOW()), (6, 2, 4, NOW()), (7, 2, 11, NOW()), (8, 2, 21, NOW()),
(9, 3, 1, NOW()), (10, 3, 3, NOW()), (11, 3, 13, NOW()), (12, 3, 23, NOW()),
(13, 4, 2, NOW()), (14, 4, 9, NOW()), (15, 4, 5, NOW()), (16, 4, 19, NOW()),
(17, 5, 1, NOW()), (18, 5, 6, NOW()), (19, 5, 26, NOW()), (20, 5, 22, NOW()),
(21, 6, 1, NOW()), (22, 6, 10, NOW()), (23, 6, 7, NOW()), (24, 6, 4, NOW()),
(25, 7, 2, NOW()), (26, 7, 25, NOW()), (27, 7, 21, NOW()), (28, 7, 18, NOW()),
(29, 8, 1, NOW()), (30, 8, 23, NOW()), (31, 8, 12, NOW()), (32, 8, 3, NOW()),
(33, 9, 2, NOW()), (34, 9, 26, NOW()), (35, 9, 6, NOW()), (36, 9, 24, NOW()),
(37, 11, 1, NOW()), (38, 11, 23, NOW()), (39, 11, 5, NOW()), (40, 13, 1, NOW());

-- =============================================
-- 5. 赛事表数据（8条，覆盖国赛、美赛、不同状态）
-- =============================================
INSERT INTO `competition` VALUES
(1, '2024年全国大学生数学建模竞赛', 'CN', '全国大学生数学建模竞赛是中国高校规模最大的基础性学科竞赛...', '2024-06-01 00:00:00', '2024-08-31 23:59:59', '2024-09-05 18:00:00', '2024-09-08 20:00:00', 'https://www.mcm.edu.cn', 'https://cdn.leetmodel.com/img/comp1.jpg', 'finished', NOW(), NOW()),
(2, '2025年美国大学生数学建模竞赛', 'EN', 'MCM/ICM is the world''s largest mathematical modeling competition...', '2024-11-01 00:00:00', '2025-01-20 23:59:59', '2025-02-06 08:00:00', '2025-02-10 08:00:00', 'https://www.comap.com', 'https://cdn.leetmodel.com/img/comp2.jpg', 'upcoming', NOW(), NOW()),
(3, '2024年全国大学生数学建模竞赛', 'CN', '同国赛...', '2024-05-01 00:00:00', '2024-07-31 23:59:59', '2024-09-12 18:00:00', '2024-09-15 20:00:00', 'https://www.mcm.edu.cn', 'https://cdn.leetmodel.com/img/comp3.jpg', 'finished', NOW(), NOW()),
(4, '2025年全国大学生数学建模竞赛', 'CN', '2025年国赛即将开始...', '2025-06-01 00:00:00', '2025-08-31 23:59:59', '2025-09-04 18:00:00', '2025-09-07 20:00:00', 'https://www.mcm.edu.cn', 'https://cdn.leetmodel.com/img/comp4.jpg', 'upcoming', NOW(), NOW()),
(5, '2024年亚太地区数学建模竞赛', 'EN', 'APMCM is a regional mathematical modeling competition...', '2024-09-01 00:00:00', '2024-10-31 23:59:59', '2024-11-21 08:00:00', '2024-11-25 08:00:00', 'https://www.apmcm.org', 'https://cdn.leetmodel.com/img/comp5.jpg', 'finished', NOW(), NOW()),
(6, '2025年数学建模美赛', 'EN', 'MCM 2025...', '2024-11-01 00:00:00', '2025-01-20 23:59:59', '2025-02-06 08:00:00', '2025-02-10 08:00:00', 'https://www.comap.com', 'https://cdn.leetmodel.com/img/comp6.jpg', 'upcoming', NOW(), NOW()),
(7, '2024年全国研究生数学建模竞赛', 'CN', '研赛是面向研究生的数学建模竞赛...', '2024-08-01 00:00:00', '2024-09-20 23:59:59', '2024-10-11 18:00:00', '2024-10-14 20:00:00', 'https://www.gmcm.edu.cn', 'https://cdn.leetmodel.com/img/comp7.jpg', 'finished', NOW(), NOW()),
(8, '2025年数学建模网络挑战赛', 'CN', '网络挑战赛是热身赛事...', '2025-03-01 00:00:00', '2025-04-30 23:59:59', '2025-05-15 18:00:00', '2025-05-18 20:00:00', 'https://www.tzmcm.cn', 'https://cdn.leetmodel.com/img/comp8.jpg', 'upcoming', NOW(), NOW());

-- =============================================
-- 6. 赛事-标签关联表数据（20条，随机组合）
-- =============================================
INSERT INTO `competition_tag` VALUES
(1, 1, 1, NOW()), (2, 1, 10, NOW()), (3, 2, 2, NOW()), (4, 2, 11, NOW()),
(5, 3, 1, NOW()), (6, 3, 12, NOW()), (7, 4, 1, NOW()), (8, 4, 13, NOW()),
(9, 5, 2, NOW()), (10, 5, 26, NOW()), (11, 6, 2, NOW()), (12, 6, 9, NOW()),
(13, 7, 1, NOW()), (14, 7, 6, NOW()), (15, 8, 1, NOW()), (16, 8, 7, NOW()),
(17, 1, 3, NOW()), (18, 2, 4, NOW()), (19, 4, 5, NOW()), (20, 7, 6, NOW());

-- =============================================
-- 7. 奖项表数据（20条，覆盖各赛事等级）
-- =============================================
INSERT INTO `prize` VALUES
(1, '全国一等奖', 1, 'first', '国赛最高奖项，要求模型创新、结果准确、论文优秀...', NOW(), NOW()),
(2, '全国二等奖', 1, 'second', '国赛二等奖，模型合理、结果正确、论文规范...', NOW(), NOW()),
(3, '全国三等奖', 1, 'third', '国赛三等奖，完成题目、模型基本合理...', NOW(), NOW()),
(4, '成功参赛奖', 1, 'excellent', '成功提交论文即可获得...', NOW(), NOW()),
(5, 'Outstanding Winner', 2, 'special', '美赛最高奖项，全球仅1%左右队伍获得...', NOW(), NOW()),
(6, 'Meritorious Winner', 2, 'first', '美赛一等奖，模型优秀、结果突出...', NOW(), NOW()),
(7, 'Honorable Mention', 2, 'second', '美赛二等奖，模型良好、结果正确...', NOW(), NOW()),
(8, 'Successful Participant', 2, 'excellent', '成功提交论文...', NOW(), NOW()),
(9, '全国一等奖', 3, 'first', '同国赛...', NOW(), NOW()),
(10, '全国二等奖', 3, 'second', '同国赛...', NOW(), NOW()),
(11, '全国一等奖', 4, 'first', '2025国赛...', NOW(), NOW()),
(12, '全国二等奖', 4, 'second', '2025国赛...', NOW(), NOW()),
(13, '亚太一等奖', 5, 'first', 'APMCM一等奖...', NOW(), NOW()),
(14, '亚太二等奖', 5, 'second', 'APMCM二等奖...', NOW(), NOW()),
(15, 'Outstanding Winner', 6, 'special', 'MCM 2025...', NOW(), NOW()),
(16, 'Meritorious Winner', 6, 'first', 'MCM 2025...', NOW(), NOW()),
(17, '全国一等奖', 7, 'first', '研赛一等奖...', NOW(), NOW()),
(18, '全国二等奖', 7, 'second', '研赛二等奖...', NOW(), NOW()),
(19, '网络挑战赛一等奖', 8, 'first', '网络赛一等奖...', NOW(), NOW()),
(20, '网络挑战赛二等奖', 8, 'second', '网络赛二等奖...', NOW(), NOW());

-- =============================================
-- 8. 组队表数据（10条，覆盖所有状态、流程）
-- =============================================
INSERT INTO `team` VALUES
(1, '清华建模天团', '目标国赛一等奖，已有建模手和编程手，缺论文手！', 4, 'recruiting', 'inner', '清华大学', 'unstart', 1, 1, 2, 'unconfirm', NULL, NULL, 3, 2, NOW(), NOW()),
(2, '北大冲奖队', '美赛M奖以上，已有建模手，缺编程和论文手', 2, 'recruiting', 'cross', '北京大学', 'unstart', 3, 3, NULL, NULL, NULL, NULL, 3, 1, NOW(), NOW()),
(3, '浙大铁三角', '国赛二等奖以上，队伍已满，正在确认中', 1, 'full', 'inner', '浙江大学', 'ongoing', 5, 5, 6, 'confirmed', 7, 'unconfirm', 3, 3, NOW(), NOW()),
(4, '复旦新星队', '目标成功参赛，新手友好，已有两人', 4, 'recruiting', 'cross', '复旦大学', 'unstart', 8, 8, 9, 'unconfirm', NULL, NULL, 3, 2, NOW(), NOW()),
(5, '上海交大战队', '美赛冲O奖，队伍已满，考核完成，等待最终确认', 2, 'full', 'inner', '上海交通大学', 'finished', 10, 10, 12, 'confirmed', 13, 'confirmed', 3, 3, NOW(), NOW()),
(6, '南大梦之队', '国赛一等奖，已有建模和论文，缺编程手', 4, 'recruiting', 'cross', '南京大学', 'unstart', 14, 14, 15, 'unconfirm', NULL, NULL, 3, 2, NOW(), NOW()),
(7, '中山光速队', '网络挑战赛热身，队伍已满，确认成功', 8, 'finished', 'inner', '中山大学', 'finished', 16, 16, 17, 'confirmed', 18, 'confirmed', 3, 3, NOW(), NOW()),
(8, '华科探索者', '研赛试水，已有两人，缺论文手', 7, 'recruiting', 'cross', '华中科技大学', 'unstart', 19, 19, 20, 'unconfirm', NULL, NULL, 3, 2, NOW(), NOW()),
(9, '西交攻坚队', '队伍已解散', 1, 'destroyed', 'inner', '西安交通大学', 'unstart', 12, 12, NULL, NULL, NULL, NULL, 3, 1, NOW(), NOW()),
(10, '哈工冲锋队', '亚太赛组队，已有建模和编程，缺论文手', 5, 'recruiting', 'cross', '哈尔滨工业大学', 'unstart', 15, 15, 16, 'unconfirm', NULL, NULL, 3, 2, NOW(), NOW());

-- =============================================
-- 9. 提交记录表数据（25条，覆盖所有角色、文件类型、状态）
-- =============================================
INSERT INTO `submission` VALUES
(1, 1, 1, 'modeler', '# 城市交通优化模型\n\n## 问题分析...', 'https://cdn.leetmodel.com/submit/1.md', 'md', 'd41d8cd98f00b204e9800998ecf8427e', 1, 1, 'reviewed', NOW(), NOW()),
(2, 2, 1, 'coder', 'import numpy as np\nimport pandas as pd\n# 交通优化代码...', 'https://cdn.leetmodel.com/submit/2.py', 'code', 'd41d8cd98f00b204e9800998ecf8427f', 1, 2, 'reviewed', NOW(), NOW()),
(3, 3, 1, 'writer', NULL, 'https://cdn.leetmodel.com/submit/3.pdf', 'pdf', 'd41d8cd98f00b204e9800998ecf84270', 1, 3, 'reviewed', NOW(), NOW()),
(4, 4, 2, 'modeler', '# 全球气温预测模型\n\n使用时间序列分析...', 'https://cdn.leetmodel.com/submit/4.md', 'md', 'd41d8cd98f00b204e9800998ecf84271', 0, NULL, 'submitted', NOW(), NOW()),
(5, 5, 3, 'coder', 'import torch\nimport torch.nn as nn\n# 医疗资源分配代码...', 'https://cdn.leetmodel.com/submit/5.py', 'code', 'd41d8cd98f00b204e9800998ecf84272', 1, 5, 'reviewed', NOW(), NOW()),
(6, 6, 6, 'writer', NULL, 'https://cdn.leetmodel.com/submit/6.pdf', 'pdf', 'd41d8cd98f00b204e9800998ecf84273', 1, 6, 'reviewed', NOW(), NOW()),
(7, 7, 7, 'modeler', '# 股票价格预测\n\n使用LSTM神经网络...', 'https://cdn.leetmodel.com/submit/7.md', 'md', 'd41d8cd98f00b204e9800998ecf84274', 0, NULL, 'draft', NOW(), NOW()),
(8, 8, 8, 'coder', 'import numpy as np\nfrom scipy.optimize import linprog\n# 快递柜布局优化...', 'https://cdn.leetmodel.com/submit/8.py', 'code', 'd41d8cd98f00b204e9800998ecf84275', 1, 8, 'reviewed', NOW(), NOW()),
(9, 9, 11, 'writer', NULL, 'https://cdn.leetmodel.com/submit/9.pdf', 'pdf', 'd41d8cd98f00b204e9800998ecf84276', 1, 9, 'reviewed', NOW(), NOW()),
(10, 10, 13, 'modeler', '# 人口增长模型\n\n使用Logistic模型...', 'https://cdn.leetmodel.com/submit/10.md', 'md', 'd41d8cd98f00b204e9800998ecf84277', 0, NULL, 'submitted', NOW(), NOW()),
(11, 12, 1, 'modeler', '# 城市交通优化（第二版）...', 'https://cdn.leetmodel.com/submit/11.md', 'md', 'd41d8cd98f00b204e9800998ecf84278', 1, 11, 'reviewed', NOW(), NOW()),
(12, 13, 2, 'coder', 'import tensorflow as tf\n# 气温预测代码...', 'https://cdn.leetmodel.com/submit/12.py', 'code', 'd41d8cd98f00b204e9800998ecf84279', 0, NULL, 'draft', NOW(), NOW()),
(13, 14, 3, 'writer', NULL, 'https://cdn.leetmodel.com/submit/13.pdf', 'pdf', 'd41d8cd98f00b204e9800998ecf8427a', 1, 13, 'reviewed', NOW(), NOW()),
(14, 15, 6, 'modeler', '# 环境污染扩散模型...', 'https://cdn.leetmodel.com/submit/14.md', 'md', 'd41d8cd98f00b204e9800998ecf8427b', 1, 14, 'reviewed', NOW(), NOW()),
(15, 16, 7, 'coder', 'import pandas as pd\nfrom sklearn.linear_model import LinearRegression\n# 股票预测代码...', 'https://cdn.leetmodel.com/submit/15.py', 'code', 'd41d8cd98f00b204e9800998ecf8427c', 0, NULL, 'submitted', NOW(), NOW()),
(16, 17, 8, 'writer', NULL, 'https://cdn.leetmodel.com/submit/16.pdf', 'pdf', 'd41d8cd98f00b204e9800998ecf8427d', 1, 16, 'reviewed', NOW(), NOW()),
(17, 18, 11, 'modeler', '# 旅游路线优化...', 'https://cdn.leetmodel.com/submit/17.md', 'md', 'd41d8cd98f00b204e9800998ecf8427e', 1, 17, 'reviewed', NOW(), NOW()),
(18, 19, 13, 'coder', 'import numpy as np\n# 人口增长代码...', 'https://cdn.leetmodel.com/submit/18.py', 'code', 'd41d8cd98f00b204e9800998ecf8427f', 0, NULL, 'draft', NOW(), NOW()),
(19, 20, 1, 'writer', NULL, 'https://cdn.leetmodel.com/submit/19.pdf', 'pdf', 'd41d8cd98f00b204e9800998ecf84280', 1, 19, 'reviewed', NOW(), NOW()),
(20, 1, 3, 'modeler', '# 医疗资源分配模型...', 'https://cdn.leetmodel.com/submit/20.md', 'md', 'd41d8cd98f00b204e9800998ecf84281', 1, 20, 'reviewed', NOW(), NOW()),
(21, 2, 6, 'coder', 'import matplotlib.pyplot as plt\n# 污染扩散可视化...', 'https://cdn.leetmodel.com/submit/21.py', 'code', 'd41d8cd98f00b204e9800998ecf84282', 0, NULL, 'submitted', NOW(), NOW()),
(22, 3, 8, 'writer', NULL, 'https://cdn.leetmodel.com/submit/22.pdf', 'pdf', 'd41d8cd98f00b204e9800998ecf84283', 1, 22, 'reviewed', NOW(), NOW()),
(23, 4, 11, 'modeler', '# 旅游路线优化（改进版）...', 'https://cdn.leetmodel.com/submit/23.md', 'md', 'd41d8cd98f00b204e9800998ecf84284', 1, 23, 'reviewed', NOW(), NOW()),
(24, 5, 13, 'coder', 'import scipy as sp\n# 人口增长拟合...', 'https://cdn.leetmodel.com/submit/24.py', 'code', 'd41d8cd98f00b204e9800998ecf84285', 0, NULL, 'draft', NOW(), NOW()),
(25, 6, 1, 'writer', NULL, 'https://cdn.leetmodel.com/submit/25.pdf', 'pdf', 'd41d8cd98f00b204e9800998ecf84286', 1, 25, 'reviewed', NOW(), NOW());

-- =============================================
-- 10. 评审结果表数据（20条，覆盖AI/人工、不同分数）
-- =============================================
INSERT INTO `review` VALUES
(1, 1, 'ai', 'CN', 85.50, 0.20, 82.00, 0.20, 88.00, 0.20, 80.00, 0.20, 86.00, 0.20, 84.30, '## 评审报告\n\n### 建模合理性：模型选择合理，层次分析法应用得当...', '### 优化建议\n1. 可以增加敏感性分析...', NOW()),
(2, 2, 'ai', 'CN', 88.00, 0.20, 90.00, 0.20, 85.00, 0.20, 87.00, 0.20, 84.00, 0.20, 86.80, '## 评审报告\n\n### 代码准确性：代码结构清晰，注释完整...', '### 优化建议\n1. 可以增加异常处理...', NOW()),
(3, 3, 'manual', 'CN', 82.00, 0.20, 80.00, 0.20, 92.00, 0.20, 88.00, 0.20, 86.00, 0.20, 85.60, '## 评审报告\n\n### 论文规范性：论文格式完全符合国赛要求...', '### 优化建议\n1. 摘要可以更精炼...', NOW()),
(5, 5, 'ai', 'CN', 78.00, 0.20, 85.00, 0.20, 80.00, 0.20, 75.00, 0.20, 78.00, 0.20, 79.20, '## 评审报告\n\n### 建模合理性：模型基本合理...', '### 优化建议\n1. 可以优化目标函数...', NOW()),
(6, 6, 'manual', 'CN', 80.00, 0.20, 78.00, 0.20, 88.00, 0.20, 82.00, 0.20, 80.00, 0.20, 81.60, '## 评审报告\n\n### 论文规范性：论文结构完整...', '### 优化建议\n1. 图表可以更美观...', NOW()),
(8, 8, 'ai', 'CN', 85.00, 0.20, 88.00, 0.20, 82.00, 0.20, 80.00, 0.20, 84.00, 0.20, 83.80, '## 评审报告\n\n### 代码准确性：代码正确，结果可靠...', '### 优化建议\n1. 可以增加代码复用...', NOW()),
(9, 9, 'manual', 'CN', 82.00, 0.20, 80.00, 0.20, 90.00, 0.20, 85.00, 0.20, 83.00, 0.20, 84.00, '## 评审报告\n\n### 论文规范性：论文写作优秀...', '### 优化建议\n1. 可以增加更多参考文献...', NOW()),
(11, 11, 'ai', 'CN', 88.00, 0.20, 85.00, 0.20, 86.00, 0.20, 84.00, 0.20, 87.00, 0.20, 86.00, '## 评审报告\n\n### 建模合理性：模型有创新...', '### 优化建议\n1. 可以增加对比实验...', NOW()),
(13, 13, 'manual', 'CN', 80.00, 0.20, 82.00, 0.20, 86.00, 0.20, 80.00, 0.20, 82.00, 0.20, 82.00, '## 评审报告\n\n### 论文规范性：论文符合要求...', '### 优化建议\n1. 关键词可以更准确...', NOW()),
(14, 14, 'ai', 'CN', 75.00, 0.20, 78.00, 0.20, 80.00, 0.20, 72.00, 0.20, 76.00, 0.20, 76.20, '## 评审报告\n\n### 建模合理性：模型基本完整...', '### 优化建议\n1. 可以优化参数选择...', NOW()),
(16, 16, 'manual', 'CN', 83.00, 0.20, 85.00, 0.20, 88.00, 0.20, 82.00, 0.20, 84.00, 0.20, 84.40, '## 评审报告\n\n### 论文规范性：论文质量较高...', '### 优化建议\n1. 可以增加更多图表...', NOW()),
(17, 17, 'ai', 'CN', 86.00, 0.20, 84.00, 0.20, 82.00, 0.20, 85.00, 0.20, 87.00, 0.20, 84.80, '## 评审报告\n\n### 建模合理性：模型应用得当...', '### 优化建议\n1. 可以增加模型解释...', NOW()),
(19, 19, 'manual', 'CN', 81.00, 0.20, 80.00, 0.20, 89.00, 0.20, 83.00, 0.20, 82.00, 0.20, 83.00, '## 评审报告\n\n### 论文规范性：论文写作规范...', '### 优化建议\n1. 可以优化结论部分...', NOW()),
(20, 20, 'ai', 'CN', 87.00, 0.20, 86.00, 0.20, 84.00, 0.20, 85.00, 0.20, 88.00, 0.20, 86.00, '## 评审报告\n\n### 建模合理性：模型设计合理...', '### 优化建议\n1. 可以增加鲁棒性分析...', NOW()),
(22, 22, 'manual', 'CN', 79.00, 0.20, 81.00, 0.20, 87.00, 0.20, 80.00, 0.20, 82.00, 0.20, 81.80, '## 评审报告\n\n### 论文规范性：论文基本合格...', '### 优化建议\n1. 可以增加更多分析...', NOW()),
(23, 23, 'ai', 'CN', 89.00, 0.20, 87.00, 0.20, 85.00, 0.20, 88.00, 0.20, 89.00, 0.20, 87.60, '## 评审报告\n\n### 建模合理性：模型有较大创新...', '### 优化建议\n1. 可以尝试更多模型...', NOW()),
(25, 25, 'manual', 'CN', 84.00, 0.20, 83.00, 0.20, 91.00, 0.20, 86.00, 0.20, 85.00, 0.20, 85.80, '## 评审报告\n\n### 论文规范性：论文非常优秀...', '### 优化建议\n1. 可以准备投稿...', NOW());

-- =============================================
-- 11. 用户训练记录表数据（20条，覆盖所有状态、角色）
-- =============================================
INSERT INTO `train_record_user` VALUES
(1, 1, 1, 'modeler', 1, 'finished', '2024-08-01 10:00:00', '2024-08-03 15:00:00', NOW(), NOW()),
(2, 2, 1, 'coder', 2, 'finished', '2024-08-01 10:00:00', '2024-08-02 18:00:00', NOW(), NOW()),
(3, 3, 1, 'writer', 3, 'finished', '2024-08-01 10:00:00', '2024-08-04 12:00:00', NOW(), NOW()),
(4, 4, 2, 'modeler', 4, 'ongoing', '2024-08-10 09:00:00', NULL, NOW(), NOW()),
(5, 5, 3, 'coder', 5, 'finished', '2024-07-15 14:00:00', '2024-07-17 16:00:00', NOW(), NOW()),
(6, 6, 6, 'writer', 6, 'finished', '2024-07-20 11:00:00', '2024-07-23 10:00:00', NOW(), NOW()),
(7, 7, 7, 'modeler', 7, 'abandoned', '2024-08-05 08:00:00', '2024-08-06 12:00:00', NOW(), NOW()),
(8, 8, 8, 'coder', 8, 'finished', '2024-07-25 13:00:00', '2024-07-27 14:00:00', NOW(), NOW()),
(9, 9, 11, 'writer', 9, 'finished', '2024-08-02 10:00:00', '2024-08-05 11:00:00', NOW(), NOW()),
(10, 10, 13, 'modeler', 10, 'ongoing', '2024-08-12 07:00:00', NULL, NOW(), NOW()),
(11, 12, 1, 'modeler', 11, 'finished', '2024-08-06 09:00:00', '2024-08-08 14:00:00', NOW(), NOW()),
(12, 13, 2, 'coder', 12, 'abandoned', '2024-08-08 10:00:00', '2024-08-09 11:00:00', NOW(), NOW()),
(13, 14, 3, 'writer', 13, 'finished', '2024-07-10 14:00:00', '2024-07-13 16:00:00', NOW(), NOW()),
(14, 15, 6, 'modeler', 14, 'finished', '2024-07-18 08:00:00', '2024-07-20 12:00:00', NOW(), NOW()),
(15, 16, 7, 'coder', 15, 'ongoing', '2024-08-15 09:00:00', NULL, NOW(), NOW()),
(16, 17, 8, 'writer', 16, 'finished', '2024-07-30 10:00:00', '2024-08-02 11:00:00', NOW(), NOW()),
(17, 18, 11, 'modeler', 17, 'finished', '2024-08-03 12:00:00', '2024-08-06 13:00:00', NOW(), NOW()),
(18, 19, 13, 'coder', 18, 'abandoned', '2024-08-07 08:00:00', '2024-08-08 09:00:00', NOW(), NOW()),
(19, 20, 1, 'writer', 19, 'finished', '2024-08-04 10:00:00', '2024-08-07 12:00:00', NOW(), NOW()),
(20, 1, 3, 'modeler', 20, 'finished', '2024-07-12 14:00:00', '2024-07-15 16:00:00', NOW(), NOW());

-- =============================================
-- 12. 自定义测试表数据（10条，覆盖所有角色、难度）
-- =============================================
INSERT INTO `custom_test` VALUES
(1, '建模手专项测试：层次分析法应用', '请使用层次分析法解决以下决策问题...', 'modeler', 11, 'medium', 'published', NOW(), NOW()),
(2, '编程手专项测试：线性规划实现', '请用Python实现线性规划求解...', 'coder', 11, 'easy', 'published', NOW(), NOW()),
(3, '论文手专项测试：摘要写作', '请根据以下材料撰写一篇符合国赛要求的摘要...', 'writer', 11, 'medium', 'published', NOW(), NOW()),
(4, '建模手进阶测试：预测模型选择', '请为以下场景选择合适的预测模型并说明理由...', 'modeler', 11, 'hard', 'published', NOW(), NOW()),
(5, '编程手进阶测试：遗传算法实现', '请实现遗传算法解决TSP问题...', 'coder', 11, 'hard', 'published', NOW(), NOW()),
(6, '论文手进阶测试：图表制作', '请根据以下数据制作符合美赛要求的图表...', 'writer', 11, 'medium', 'published', NOW(), NOW()),
(7, '建模手入门测试：问题分析', '请对以下数学建模问题进行初步分析...', 'modeler', 1, 'easy', 'published', NOW(), NOW()),
(8, '编程手入门测试：数据处理', '请用Python处理以下CSV数据...', 'coder', 2, 'easy', 'published', NOW(), NOW()),
(9, '论文手入门测试：论文结构', '请列出数学建模论文的基本结构...', 'writer', 3, 'easy', 'published', NOW(), NOW()),
(10, '建模手综合测试：模型创新', '请针对以下问题提出创新性的解决方案...', 'modeler', 11, 'hard', 'unreviewed', NOW(), NOW());

-- =============================================
-- 13. 社区帖子表数据（15条，覆盖所有类型、状态）
-- =============================================
INSERT INTO `post` VALUES
(1, 1, 'experience', '2024国赛一等奖获奖心得', '## 前言\n\n非常荣幸能在2024年国赛中获得一等奖...', 128, 35, 2560, 850, 1, 'published', NOW(), NOW()),
(2, 2, 'skill', '美赛LaTeX模板使用指南', '美赛要求使用LaTeX提交论文，这里分享一个好用的模板...', 256, 67, 5120, 1200, 1, 'published', NOW(), NOW()),
(3, 3, 'discuss', '美赛选MCM还是ICM？', '马上要美赛了，纠结选MCM还是ICM，大家有什么建议？', 89, 120, 3200, 950, 0, 'published', NOW(), NOW()),
(4, 4, 'experience', '从零基础到国赛二等奖的三个月', '作为一名纯文科学生，三个月前我还不知道数学建模是什么...', 167, 45, 4000, 880, 0, 'published', NOW(), NOW()),
(5, 5, 'skill', 'Python数据可视化完全指南', '本文介绍matplotlib、seaborn、plotly等可视化库的使用...', 312, 89, 6200, 1500, 1, 'published', NOW(), NOW()),
(6, 6, 'discuss', '数学建模常用模型有哪些？', '整理了一些数学建模常用的模型，大家补充一下...', 145, 78, 2900, 720, 0, 'published', NOW(), NOW()),
(7, 7, 'experience', '美赛M奖之路', '参加了两次美赛，第一次SP，第二次M，分享一下经验...', 98, 32, 2450, 680, 0, 'published', NOW(), NOW()),
(8, 8, 'skill', '层次分析法(AHP)从入门到精通', '详细讲解AHP的原理、步骤、代码实现...', 278, 95, 5560, 1350, 0, 'published', NOW(), NOW()),
(9, 9, 'discuss', '如何找靠谱的队友？', '组队太重要了，大家都是怎么找到靠谱队友的？', 112, 156, 3800, 890, 0, 'published', NOW(), NOW()),
(10, 10, 'experience', '数模入门一个月的感受', '接触数学建模一个月了，分享一下我的学习路线...', 76, 28, 1900, 520, 0, 'published', NOW(), NOW()),
(11, 12, 'skill', '遗传算法原理与Python实现', '讲解遗传算法的选择、交叉、变异操作...', 189, 56, 3780, 980, 0, 'unreviewed', NOW(), NOW()),
(12, 13, 'discuss', '国赛和美赛的区别是什么？', '参加过国赛，想试试美赛，两者有什么不同？', 134, 87, 2680, 750, 0, 'published', NOW(), NOW()),
(13, 14, 'experience', '研赛参赛经验分享', '刚参加完研赛，分享一下准备过程和心得体会...', 85, 23, 2100, 590, 0, 'published', NOW(), NOW()),
(14, 15, 'skill', '时间序列分析方法总结', 'ARIMA、指数平滑、LSTM等时间序列方法对比...', 223, 71, 4460, 1100, 0, 'rejected', NOW(), NOW()),
(15, 16, 'discuss', '数学建模对就业有帮助吗？', '一直在想，花这么多时间在数模上，对找工作有用吗？', 178, 134, 4200, 920, 0, 'published', NOW(), NOW());

-- =============================================
-- 14. 帖子-标签关联表数据（30条，随机组合）
-- =============================================
INSERT INTO `post_tag` VALUES
(1, 1, 1, NOW()), (2, 1, 14, NOW()), (3, 1, 30, NOW()), (4, 2, 2, NOW()),
(5, 2, 16, NOW()), (6, 2, 29, NOW()), (7, 3, 2, NOW()), (8, 3, 15, NOW()),
(9, 4, 1, NOW()), (10, 4, 14, NOW()), (11, 4, 29, NOW()), (12, 5, 6, NOW()),
(13, 5, 16, NOW()), (14, 5, 22, NOW()), (15, 6, 3, NOW()), (16, 6, 4, NOW()),
(17, 6, 15, NOW()), (18, 7, 2, NOW()), (19, 7, 14, NOW()), (20, 7, 30, NOW()),
(21, 8, 3, NOW()), (22, 8, 16, NOW()), (23, 8, 17, NOW()), (24, 9, 15, NOW()),
(25, 9, 29, NOW()), (26, 10, 14, NOW()), (27, 10, 29, NOW()), (28, 12, 1, NOW()),
(29, 12, 2, NOW()), (30, 13, 14, NOW());

-- =============================================
-- 15. 评论表数据（40条，覆盖一级、二级回复）
-- =============================================
INSERT INTO `comment` VALUES
(1, 1, 2, NULL, '恭喜恭喜！太厉害了，能分享一下你们的训练计划吗？', 45, 'normal', NOW(), NOW()),
(2, 1, 3, NULL, '一等奖！膜拜大佬，请问用的是什么模型？', 32, 'normal', NOW(), NOW()),
(3, 1, 1, 1, '谢谢！我们的训练计划是每周一套题...', 28, 'normal', NOW(), NOW()),
(4, 1, 4, 2, '主要用了层次分析法和线性规划...', 21, 'normal', NOW(), NOW()),
(5, 2, 5, NULL, '这个模板太好用了！感谢分享！', 67, 'normal', NOW(), NOW()),
(6, 2, 6, NULL, '请问能分享一下完整的.tex文件吗？', 45, 'normal', NOW(), NOW()),
(7, 2, 2, 5, '很高兴能帮到你！', 12, 'normal', NOW(), NOW()),
(8, 3, 7, NULL, '建议选MCM，题目更具体，容易上手...', 56, 'normal', NOW(), NOW()),
(9, 3, 8, NULL, 'ICM更看重创新性，适合有想法的队伍...', 48, 'normal', NOW(), NOW()),
(10, 3, 9, NULL, '同纠结，蹲一个答案...', 23, 'normal', NOW(), NOW()),
(11, 4, 10, NULL, '太励志了！文科都能拿二等奖，我也要加油！', 34, 'normal', NOW(), NOW()),
(12, 4, 1, NULL, '谢谢！只要肯花时间，一定可以的！', 21, 'normal', NOW(), NOW()),
(13, 5, 12, NULL, '收藏了！这篇太全了，正好需要...', 78, 'normal', NOW(), NOW()),
(14, 5, 13, NULL, '请问有plotly的中文教程吗？', 32, 'normal', NOW(), NOW()),
(15, 5, 5, 13, '有的，后续会更新...', 18, 'normal', NOW(), NOW()),
(16, 6, 14, NULL, '补充：模拟退火算法也很常用...', 41, 'normal', NOW(), NOW()),
(17, 6, 15, NULL, '还有神经网络，现在越来越流行了...', 35, 'normal', NOW(), NOW()),
(18, 6, 6, NULL, '整理得很全，收藏了！', 27, 'normal', NOW(), NOW()),
(19, 7, 16, NULL, '两次就M奖，太强了！', 29, 'normal', NOW(), NOW()),
(20, 7, 7, NULL, '能分享一下你们的论文写作技巧吗？', 24, 'normal', NOW(), NOW()),
(21, 8, 17, NULL, 'AHP终于搞懂了，感谢！', 56, 'normal', NOW(), NOW()),
(22, 8, 18, NULL, '请问有代码的GitHub链接吗？', 38, 'normal', NOW(), NOW()),
(23, 8, 8, 22, '有的，在文章末尾...', 15, 'normal', NOW(), NOW()),
(24, 9, 19, NULL, '同问，找队友太难了...', 67, 'normal', NOW(), NOW()),
(25, 9, 20, NULL, '建议在学校的数模群里找...', 52, 'normal', NOW(), NOW()),
(26, 9, 9, 25, '我们学校数模群人太少了...', 28, 'normal', NOW(), NOW()),
(27, 10, 1, NULL, '这个学习路线很合理，加油！', 23, 'normal', NOW(), NOW()),
(28, 10, 2, NULL, '一个月就能入门，好快！', 19, 'normal', NOW(), NOW()),
(29, 12, 3, NULL, '国赛更看重结果正确性，美赛更看重创新...', 45, 'normal', NOW(), NOW()),
(30, 12, 4, NULL, '国赛是中文，美赛是英文，这也是很大的区别...', 38, 'normal', NOW(), NOW()),
(31, 13, 5, NULL, '研赛的题目难度更大啊...', 21, 'normal', NOW(), NOW()),
(32, 13, 6, NULL, '感谢分享，正准备参加研赛...', 17, 'normal', NOW(), NOW()),
(33, 15, 7, NULL, '当然有用！简历上写数模获奖很加分...', 56, 'normal', NOW(), NOW()),
(34, 15, 8, NULL, '对找算法、数据分析类的工作很有帮助...', 48, 'normal', NOW(), NOW()),
(35, 15, 9, NULL, '主要是锻炼思维能力和解决问题的能力...', 42, 'normal', NOW(), NOW()),
(36, 1, 5, NULL, '能分享一下你们的分工吗？', 18, 'normal', NOW(), NOW()),
(37, 1, 1, 36, '我是建模手，主要负责模型建立...', 12, 'normal', NOW(), NOW()),
(38, 2, 7, NULL, '正好需要，太感谢了！', 25, 'normal', NOW(), NOW()),
(39, 3, 10, NULL, '还是看队伍擅长什么吧...', 15, 'normal', NOW(), NOW()),
(40, 5, 8, NULL, '收藏夹吃灰系列...', 32, 'normal', NOW(), NOW());