-- 1. AI 评分报告主表
CREATE TABLE `problem_review_report` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    `modeling_problem_title` VARCHAR(255) NOT NULL COMMENT '数学建模题目全称',
    `scoring_date` DATE NOT NULL COMMENT '评分日期',
    `max_score` INT DEFAULT 100 COMMENT '总分满分',
    `weighted_total_score` DECIMAL(5, 2) DEFAULT 0.00 COMMENT '加权后的最终得分',
    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '记录更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 评分报告主表';

-- 2. AI 评分维度详情表
CREATE TABLE `problem_review_dimension` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    `report_id` BIGINT NOT NULL COMMENT '关联的评分报告ID',
    `dimension_index` INT NOT NULL COMMENT '维度序号 (1, 2, 3...)',
    `dimension_name` VARCHAR(100) NOT NULL COMMENT '维度名称 (如：问题拆解与假设合理性)',
    `weight` DECIMAL(4, 2) NOT NULL COMMENT '维度权重 (如：0.15)',
    `dimension_score` INT DEFAULT 0 COMMENT '该维度得分',
    `scoring_reason` TEXT COMMENT '评分具体理由',
    FOREIGN KEY (`report_id`) REFERENCES `problem_review_report`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 评分维度详情表';

