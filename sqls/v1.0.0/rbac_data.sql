-- =============================================================
-- RBAC 初始化数据
-- 角色体系：成员（MEMBER）→ 普通管理员（ADMIN）→ 系统管理员（SUPER_ADMIN）
-- 只有系统管理员有权管理 RBAC（角色、权限、授权）相关内容
-- =============================================================

SET NAMES utf8mb4;

-- ----------------------------
-- 角色数据
-- ----------------------------
INSERT INTO `role` (`role_id`, `name`, `code`, `description`, `status`, `create_time`, `update_time`)
VALUES
(1, '成员',      'MEMBER',      '普通成员，无管理权限',                           1, NOW(), NOW()),
(2, '普通管理员', 'ADMIN',       '可管理题目、标签、作品等业务内容',               1, NOW(), NOW()),
(3, '系统管理员', 'SUPER_ADMIN', '系统最高权限，可进行 RBAC 管理及所有业务操作', 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`), `code` = VALUES(`code`), `description` = VALUES(`description`);

-- ----------------------------
-- 权限数据
-- ----------------------------
INSERT INTO `permission` (`permission_id`, `name`, `code`, `description`, `status`, `create_time`, `update_time`)
VALUES
(1, '首页概览', 'DASHBOARD_VIEW',     '查看管理端首页概览',      1, NOW(), NOW()),
(2, '题目管理', 'PROBLEM_MANAGE',     '题目的增删查改',          1, NOW(), NOW()),
(3, '作品管理', 'SUBMISSION_MANAGE',  '作品（提交）的增删查改',  1, NOW(), NOW()),
(4, '标签管理', 'TAG_MANAGE',         '标签分类与标签的增删查改', 1, NOW(), NOW()),
(5, '角色管理', 'ROLE_MANAGE',        '角色的增删查改',          1, NOW(), NOW()),
(6, '权限管理', 'PERMISSION_MANAGE',  '权限的增删查改',          1, NOW(), NOW()),
(7, '授权管理', 'AUTH_MANAGE',        '用户-角色、角色-权限的关联管理', 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`), `code` = VALUES(`code`), `description` = VALUES(`description`);

-- ----------------------------
-- 角色-权限关联
-- ----------------------------
-- 系统管理员：拥有全部权限
INSERT INTO `role_permission` (`role_id`, `permission_id`, `create_time`, `update_time`)
SELECT 3, permission_id, NOW(), NOW() FROM `permission`
WHERE NOT EXISTS (SELECT 1 FROM `role_permission` WHERE `role_id` = 3 AND `permission_id` = `permission`.`permission_id`);

-- 普通管理员：首页概览 + 题目管理 + 作品管理 + 标签管理
INSERT IGNORE INTO `role_permission` (`role_id`, `permission_id`, `create_time`, `update_time`) VALUES
(2, 1, NOW(), NOW()),
(2, 2, NOW(), NOW()),
(2, 3, NOW(), NOW()),
(2, 4, NOW(), NOW());

-- 成员：仅首页概览
INSERT IGNORE INTO `role_permission` (`role_id`, `permission_id`, `create_time`, `update_time`) VALUES
(1, 1, NOW(), NOW());

-- ----------------------------
-- 用户-角色关联
-- 将 admin@email.com（假定 user_id=1001）设为系统管理员
-- ----------------------------
INSERT IGNORE INTO `user_role` (`user_id`, `role_id`, `create_time`, `update_time`) VALUES
(1001, 3, NOW(), NOW());

-- =============================================================
-- 权限矩阵一览
-- =============================================================
-- 权限               | 成员 | 普通管理员 | 系统管理员
-- -------------------|------|-----------|-----------
-- 首页概览            |  ✓  |    ✓     |    ✓
-- 题目管理            |  -  |    ✓     |    ✓
-- 作品管理            |  -  |    ✓     |    ✓
-- 标签管理            |  -  |    ✓     |    ✓
-- 角色管理            |  -  |    -     |    ✓
-- 权限管理            |  -  |    -     |    ✓
-- 授权管理            |  -  |    -     |    ✓
-- =============================================================
