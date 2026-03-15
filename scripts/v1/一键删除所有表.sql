-- 关闭外键检查，避免因依赖关系报错
SET FOREIGN_KEY_CHECKS = 0;

-- 一键删除所有表（按表名顺序，IF EXISTS 避免表不存在时报错）
DROP TABLE IF EXISTS `comment`;
DROP TABLE IF EXISTS `post_tag`;
DROP TABLE IF EXISTS `post`;
DROP TABLE IF EXISTS `custom_test`;
DROP TABLE IF EXISTS `train_record_user`;
DROP TABLE IF EXISTS `review`;
DROP TABLE IF EXISTS `submission`;
DROP TABLE IF EXISTS `team`;
DROP TABLE IF EXISTS `prize`;
DROP TABLE IF EXISTS `competition_tag`;
DROP TABLE IF EXISTS `competition`;
DROP TABLE IF EXISTS `problem_tag`;
DROP TABLE IF EXISTS `problem`;
DROP TABLE IF EXISTS `tag`;
DROP TABLE IF EXISTS `user`;

-- 重新开启外键检查
SET FOREIGN_KEY_CHECKS = 1;