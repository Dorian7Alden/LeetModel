ALTER TABLE `tag`
  ADD COLUMN `type` VARCHAR(32) NULL COMMENT '标签类型：BACKGROUND_DOMAIN、PROBLEM_TYPE、MODEL_ALGORITHM' AFTER `name`;

-- 历史标签没有分类信息，统一归入模型与算法，后续可由管理员按实际含义修正。
UPDATE `tag` SET `type` = 'MODEL_ALGORITHM' WHERE `type` IS NULL;

ALTER TABLE `tag`
  MODIFY COLUMN `type` VARCHAR(32) NOT NULL COMMENT '标签类型：BACKGROUND_DOMAIN、PROBLEM_TYPE、MODEL_ALGORITHM',
  ADD INDEX `idx_type` (`type`);

UPDATE `contest`
SET `name` = 'LeetModel 力模数学建模竞赛'
WHERE `code` = 'LM';
