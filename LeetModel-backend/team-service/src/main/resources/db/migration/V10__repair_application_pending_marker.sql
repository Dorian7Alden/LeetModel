-- pending_marker 仅用于约束同一用户对同一队伍最多存在一条待审核申请。
-- 修复历史版本中状态已结束但 marker 未清理的记录，恢复取消后重新申请能力。
UPDATE `team_join_application`
SET `pending_marker` = NULL
WHERE `status` <> 'pending'
  AND `pending_marker` IS NOT NULL;
