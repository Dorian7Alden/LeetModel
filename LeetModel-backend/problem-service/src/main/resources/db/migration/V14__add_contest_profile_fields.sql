-- =============================================================
-- LeetModel Problem 服务 DDL — V14 补充赛事学术档案与官方规约字段
-- =============================================================

ALTER TABLE `contest`
  ADD COLUMN `english_name`    VARCHAR(200) NULL COMMENT '英文官方全称' AFTER `name`,
  ADD COLUMN `schedule_desc`   VARCHAR(100) NULL COMMENT '赛程周期与时限描述' AFTER `english_name`,
  ADD COLUMN `team_rules`      VARCHAR(100) NULL COMMENT '组队规程说明' AFTER `schedule_desc`,
  ADD COLUMN `submission_spec` VARCHAR(150) NULL COMMENT '成果交付规范' AFTER `team_rules`,
  ADD COLUMN `problem_spec`    VARCHAR(150) NULL COMMENT '赛题命题范式' AFTER `submission_spec`,
  ADD COLUMN `description`     VARCHAR(500) NULL COMMENT '赛事权威客观简介' AFTER `problem_spec`,
  ADD COLUMN `official_url`    VARCHAR(255) NULL COMMENT '赛事官方主页链接' AFTER `description`;

-- 更新预置赛事详细档案数据

-- 1. 美国大学生数学建模竞赛 (MCM/ICM)
UPDATE `contest`
SET
  `english_name` = 'Mathematical Contest in Modeling & Interdisciplinary Contest in Modeling',
  `schedule_desc` = '每年 2 月中旬 · 连续 96 小时（4天4夜）',
  `team_rules` = '支持跨校组队 · 可由指导教师或队伍自主报名',
  `submission_spec` = '英文学术论文（PDF 格式）与代码支撑材料',
  `problem_spec` = '涵盖连续型、离散型、大数据、网络运筹、环境科学与政策分析',
  `description` = '由美国数学及其应用联合会（COMAP）主办的国际性数学建模竞赛，始于 1985 年，强调全英文学术表达、开放性建模假设与跨学科实际问题求解。',
  `official_url` = 'https://www.comap.com/contests/mcm-icm'
WHERE `id` = 1;

-- 2. 全国大学生数学建模竞赛 (CUMCM)
UPDATE `contest`
SET
  `english_name` = 'Contemporary Undergraduate Mathematical Contest in Modeling',
  `schedule_desc` = '每年 9 月上旬 · 连续 72 小时（3天3夜）',
  `team_rules` = '不可跨校组队 · 以学校为单位统一组织报名',
  `submission_spec` = '中文学术论文（PDF 格式）与支撑材料（代码/数据）',
  `problem_spec` = '涵盖物理机理推导、运筹优化调度与大数据分析应用',
  `description` = '创办于 1992 年，由中国工业与应用数学学会（CSIAM）主办，是中国高校规模最大、公信力最高的基础学科赛事，重点考查机理推导、运筹优化算法与学术规范性。',
  `official_url` = 'http://www.cumcm.cn/'
WHERE `id` = 2;

-- 3. LeetModel 力模数学建模竞赛 (LM)
UPDATE `contest`
SET
  `english_name` = 'LeetModel Mathematical Modeling Simulation Contest',
  `schedule_desc` = '常态化全真演练 · 随时自选时限开赛（72~96小时）',
  `team_rules` = '支持跨校与自由组队 · 平台个人或小队自主报名',
  `submission_spec` = 'PDF 学术论文（支持平台双阶段 AI 自动化深度合规审查）',
  `problem_spec` = '涵盖前沿经典机理建模、优化决策与时序预测',
  `description` = 'LeetModel 官方开设的常态化全真模拟沙盒，提供题面研读、倒计时实训、大文件断点上传与 AI 自动化论文体检诊断。',
  `official_url` = NULL
WHERE `id` = 3;
