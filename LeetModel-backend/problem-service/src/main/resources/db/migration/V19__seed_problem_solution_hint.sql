UPDATE `problem`
SET `solution_hint` = '先把各把手抽象为螺线上的运动点，用递推关系传播位置和速度，再逐步加入碰撞、调头与限速约束。'
WHERE `title` LIKE '%板凳龙%'
  AND (`solution_hint` IS NULL OR `solution_hint` = '');
