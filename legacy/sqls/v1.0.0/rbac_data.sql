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
(1, '成员',      'MEMBER',      '普通注册用户，无管理权限',                             1, NOW(), NOW()),
(2, '普通管理员', 'ADMIN',       '可管理题目、标签、作品等业务内容',                 1, NOW(), NOW()),
(3, '系统管理员', 'SUPER_ADMIN', '系统最高权限，可进行 RBAC 管理及所有业务操作',   1, NOW(), NOW())
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`), `code` = VALUES(`code`), `description` = VALUES(`description`);

-- ----------------------------
-- 权限数据（18 个，按资源域 + 读写粒度划分）
-- ----------------------------
INSERT INTO `permission` (`permission_id`, `name`, `code`, `description`, `status`, `create_time`, `update_time`)
VALUES
-- 系统类
(1,  '首页概览',  'DASHBOARD_VIEW',     '查看管理端首页概览',                      1, NOW(), NOW()),
(18, '文件上传',  'FILE_UPLOAD',         '上传文件到 OSS',                         1, NOW(), NOW()),

-- 用户管理
(2,  '查看用户',  'USER_VIEW',           '查看用户列表与详情',                      1, NOW(), NOW()),
(3,  '修改用户',  'USER_UPDATE',         '修改用户信息',                            1, NOW(), NOW()),
(4,  '删除用户',  'USER_DELETE',         '删除用户账号',                            1, NOW(), NOW()),

-- 题目管理
(5,  '查看题目',  'PROBLEM_VIEW',        '查看题目列表与详情（含筛选、分页）',      1, NOW(), NOW()),
(6,  '管理题目',  'PROBLEM_MANAGE',      '题目的新增、修改、删除',                  1, NOW(), NOW()),

-- 作品管理
(7,  '查看作品',  'SUBMISSION_VIEW',     '查看作品（提交）列表与详情',              1, NOW(), NOW()),
(8,  '管理作品',  'SUBMISSION_MANAGE',   '作品的新增、修改、删除',                  1, NOW(), NOW()),

-- 标签管理
(9,  '查看标签',  'TAG_VIEW',            '查看标签分类与标签列表',                  1, NOW(), NOW()),
(10, '管理标签',  'TAG_MANAGE',          '标签的新增、修改、删除',                  1, NOW(), NOW()),

-- 内容管理
(11, '查看帖子',  'POST_VIEW',           '查看帖子列表与详情',                      1, NOW(), NOW()),
(12, '查看赛事',  'CONTEST_VIEW',        '查看赛事列表与详情',                      1, NOW(), NOW()),

-- RBAC 管理（仅系统管理员）
(13, '查看角色',  'ROLE_VIEW',           '查看角色列表、详情及关联权限',            1, NOW(), NOW()),
(14, '管理角色',  'ROLE_MANAGE',         '角色的新增、修改、删除',                  1, NOW(), NOW()),
(15, '查看权限',  'PERMISSION_VIEW',     '查看权限列表与详情',                      1, NOW(), NOW()),
(16, '管理权限',  'PERMISSION_MANAGE',   '权限的新增、修改、删除',                  1, NOW(), NOW()),
(17, '授权管理',  'AUTH_MANAGE',         '用户-角色、角色-权限的关联分配',          1, NOW(), NOW())
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`), `code` = VALUES(`code`), `description` = VALUES(`description`);

-- ----------------------------
-- 角色-权限关联
-- ----------------------------
-- 系统管理员：拥有全部 18 个权限
INSERT INTO `role_permission` (`role_id`, `permission_id`, `create_time`, `update_time`)
SELECT 3, permission_id, NOW(), NOW() FROM `permission`
WHERE NOT EXISTS (SELECT 1 FROM `role_permission` WHERE `role_id` = 3 AND `permission_id` = `permission`.`permission_id`);

-- 普通管理员：业务内容管理（首页 + 查看用户 + 题目/作品/标签/帖子/赛事 + 文件上传）
INSERT IGNORE INTO `role_permission` (`role_id`, `permission_id`, `create_time`, `update_time`) VALUES
(2,  1, NOW(), NOW()), -- 首页概览
(2,  2, NOW(), NOW()), -- 查看用户
(2,  5, NOW(), NOW()), -- 查看题目
(2,  6, NOW(), NOW()), -- 管理题目
(2,  7, NOW(), NOW()), -- 查看作品
(2,  8, NOW(), NOW()), -- 管理作品
(2,  9, NOW(), NOW()), -- 查看标签
(2, 10, NOW(), NOW()), -- 管理标签
(2, 11, NOW(), NOW()), -- 查看帖子
(2, 12, NOW(), NOW()), -- 查看赛事
(2, 18, NOW(), NOW()); -- 文件上传

-- 成员：无管理权限（不分配任何 role_permission）

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
-- DASHBOARD_VIEW     |  -  |    ✓     |    ✓
-- USER_VIEW          |  -  |    ✓     |    ✓
-- USER_UPDATE        |  -  |    -     |    ✓
-- USER_DELETE        |  -  |    -     |    ✓
-- PROBLEM_VIEW       |  -  |    ✓     |    ✓
-- PROBLEM_MANAGE     |  -  |    ✓     |    ✓
-- SUBMISSION_VIEW    |  -  |    ✓     |    ✓
-- SUBMISSION_MANAGE  |  -  |    ✓     |    ✓
-- TAG_VIEW           |  -  |    ✓     |    ✓
-- TAG_MANAGE         |  -  |    ✓     |    ✓
-- POST_VIEW          |  -  |    ✓     |    ✓
-- CONTEST_VIEW       |  -  |    ✓     |    ✓
-- ROLE_VIEW          |  -  |    -     |    ✓
-- ROLE_MANAGE        |  -  |    -     |    ✓
-- PERMISSION_VIEW    |  -  |    -     |    ✓
-- PERMISSION_MANAGE  |  -  |    -     |    ✓
-- AUTH_MANAGE        |  -  |    -     |    ✓
-- FILE_UPLOAD        |  -  |    ✓     |    ✓
-- =============================================================
