-- ==================== 用户演示数据 ====================
-- 由 LeetModel-mock/scripts/generate_user_service_demo.py 生成
-- 统一演示密码：123456
-- 角色：admin=1, vip=2, user=3
-- 状态：1=正常, 0=禁用

INSERT INTO user (id, username, password, nickname, email, avatar_url, status, create_time, update_time, deleted)
VALUES
(1001, 'admin', '$2b$12$jAAJkcguTFZyICIVjECp8eLTUQ6cbuXRmjehNBC/.f/LlEqxjQyGi', '系统管理员', 'admin@leetmodel.local', 'https://api.dicebear.com/9.x/micah/svg?seed=20260822-0', 1, NOW(), NOW(), 0),
(1002, 'vip_demo', '$2b$12$qwy0qPzLMs/oCk20rpoaAu3aecViBJNCA3VJhWAzRYkdNwaP98q5C', '演示VIP用户', 'vip@leetmodel.local', 'https://api.dicebear.com/9.x/micah/svg?seed=20260822-1', 1, NOW(), NOW(), 0),
(1003, 'fuxiulan', '$2b$12$di1dZLy/2RyYkPNIr2cRe./nxChV1OjKbnDuD1KWa63h20.FaqFqy', '李建', 'fuxiulan@example.net', 'https://api.dicebear.com/9.x/micah/svg?seed=20260822-2', 1, NOW(), NOW(), 0),
(1004, 'jiebai', '$2b$12$W9FEDB/m1l3MBovet5WPnem3Vzu6Z7t9sJurBzLavPrAwBKDI71/a', '王慧', 'jiebai@example.com', 'https://api.dicebear.com/9.x/micah/svg?seed=20260822-3', 1, NOW(), NOW(), 0),
(1005, 'min31', '$2b$12$2PREqgK4oFHeASIWf6A5du5SmtGY/Mq6nHXjMSy6BtrFIHI9wn6YG', '王璐', 'yang31@example.com', 'https://api.dicebear.com/9.x/micah/svg?seed=20260822-4', 1, NOW(), NOW(), 0),
(1006, 'pwang', '$2b$12$boLd73Diq0oTYO/uELBYM.E.vvzEejbYmYsV5Ayjz9Hr/.hTxPbb2', '黄淑珍', 'mona@example.net', 'https://api.dicebear.com/9.x/micah/svg?seed=20260822-5', 1, NOW(), NOW(), 0),
(1007, 'mona', '$2b$12$8OYxRau8GlrXk/Z3JNGew.g/jPg6a1Cm.PkRnfhK6FDhwLmM8gwLK', '梁慧', 'xujie@example.net', 'https://api.dicebear.com/9.x/micah/svg?seed=20260822-6', 1, NOW(), NOW(), 0),
(1008, 'xujie', '$2b$12$aPr7JR9Ozv3yFpBys1GUH.R1eozkPzQdZgYZTSc5ebOxyRfH.Y2l.', '刘凤兰', 'guiying48@example.org', 'https://api.dicebear.com/9.x/micah/svg?seed=20260822-7', 1, NOW(), NOW(), 0),
(1009, 'guiying48', '$2b$12$hGhGarB0RDfTAT91wSicvO2pRMchMgnQZVOQx90YP4an4zz807wCO', '吴晶', 'uchang@example.net', 'https://api.dicebear.com/9.x/micah/svg?seed=20260822-8', 1, NOW(), NOW(), 0),
(1010, 'qiaochao', '$2b$12$pNtxcFa6Fs9mC2ItQUDknOkw3x/tsNkmdkcjlunz2c/vjfgCjnCF6', '张岩', 'hanyang@example.org', 'https://api.dicebear.com/9.x/micah/svg?seed=20260822-9', 1, NOW(), NOW(), 0),
(1011, 'xiuying02', '$2b$12$O15pzJjBYLeaIOeRaEnSruJjsj8dkRagE9goK92G.g/fYB1T6f5q2', '雷桂兰', 'xia42@example.com', 'https://api.dicebear.com/9.x/micah/svg?seed=20260822-10', 1, NOW(), NOW(), 0),
(1012, 'mingxia', '$2b$12$g0xi6wJYnSrw7pyhRRjU2e1OyiqcGGISimSuWzf7pVwwpHis4eQRa', '杨健', 'wufang@example.com', 'https://api.dicebear.com/9.x/micah/svg?seed=20260822-11', 0, NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE username = VALUES(username);

INSERT INTO user_role (id, user_id, role_id)
VALUES
(1, 1001, 1),
(2, 1002, 2),
(3, 1003, 3),
(4, 1004, 3),
(5, 1005, 3),
(6, 1006, 3),
(7, 1007, 3),
(8, 1008, 3),
(9, 1009, 3),
(10, 1010, 3),
(11, 1011, 3),
(12, 1012, 3)
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);
