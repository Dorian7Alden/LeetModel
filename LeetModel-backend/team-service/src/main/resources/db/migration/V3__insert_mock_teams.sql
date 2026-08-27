-- ==================== 团队演示数据 ====================
-- 由 LeetModel-mock/scripts/generate_team_service_demo.py 生成
-- 用户 ID 引用 user-service V3 演示数据
-- 仅使用状态正常的 1002 至 1010，禁用用户 1012 不参与组队

INSERT INTO `team` (id, name, description, leader_id, max_members, status, create_time, update_time, deleted)
VALUES
(2001, '星河建模队', '满员团队，覆盖三类专业角色和成员多角色场景', 1002, 3, 1, '2026-01-14 10:58:54', '2026-01-14 10:58:54', 0),
(2002, '独立思考队', '单人团队，专业角色保持默认未选择状态', 1005, 3, 1, '2026-03-12 03:29:11', '2026-03-12 03:29:11', 0),
(2003, '数模探索队', '未满员团队，覆盖一人兼任编程手和论文手场景', 1006, 3, 1, '2026-04-26 03:52:44', '2026-04-26 03:52:44', 0),
(2004, '往届挑战队', '已解散满员团队，用于验证历史成员数据留存', 1008, 3, 0, '2026-05-28 03:16:27', '2026-07-11 15:17:06', 0)
ON DUPLICATE KEY UPDATE name = VALUES(name), description = VALUES(description), leader_id = VALUES(leader_id), max_members = VALUES(max_members), status = VALUES(status), deleted = VALUES(deleted);

INSERT INTO team_member (id, team_id, user_id, role, modeler, programmer, writer, joined_at, create_time)
VALUES
(3001, 2001, 1002, 'leader', 1, 0, 1, '2026-02-01 20:27:46', '2026-02-01 20:27:46'),
(3002, 2001, 1003, 'member', 0, 1, 0, '2026-03-09 16:51:25', '2026-03-09 16:51:25'),
(3003, 2001, 1004, 'member', 0, 0, 1, '2026-03-12 02:04:30', '2026-03-12 02:04:30'),
(3004, 2002, 1005, 'leader', 0, 0, 0, '2026-03-31 10:34:14', '2026-03-31 10:34:14'),
(3005, 2003, 1006, 'leader', 1, 0, 0, '2026-05-18 15:20:55', '2026-05-18 15:20:55'),
(3006, 2003, 1007, 'member', 0, 1, 1, '2026-05-24 08:56:10', '2026-05-24 08:56:10'),
(3007, 2004, 1008, 'leader', 1, 1, 1, '2026-06-06 05:04:16', '2026-06-06 05:04:16'),
(3008, 2004, 1009, 'member', 1, 0, 0, '2026-06-20 12:57:37', '2026-06-20 12:57:37'),
(3009, 2004, 1010, 'member', 0, 1, 0, '2026-07-11 15:17:06', '2026-07-11 15:17:06')
ON DUPLICATE KEY UPDATE role = VALUES(role), modeler = VALUES(modeler), programmer = VALUES(programmer), writer = VALUES(writer), joined_at = VALUES(joined_at);
