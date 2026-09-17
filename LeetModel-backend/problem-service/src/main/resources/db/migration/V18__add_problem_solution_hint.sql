ALTER TABLE `problem`
    ADD COLUMN `solution_hint` VARCHAR(200) DEFAULT NULL COMMENT '精短解题提示，只说明建模切入点' AFTER `content_markdown`;

UPDATE `problem`
SET `solution_hint` = '先把各把手抽象为螺线上的运动点，用递推关系传播位置和速度，再逐步加入碰撞、调头与限速约束。'
WHERE `title` = '“板凳龙” 闹元宵'
  AND (`solution_hint` IS NULL OR `solution_hint` = '');
