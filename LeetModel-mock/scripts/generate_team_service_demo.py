from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent.parent
MOCK_ROOT = ROOT / "LeetModel-mock"
sys.path.insert(0, str(MOCK_ROOT))

from app.snowflake import Snowflake

OUTPUT_PATH = ROOT / "LeetModel-backend/team-service/src/main/resources/db/migration/V8__refresh_team_demo_data.sql"

# 2025 美赛真实题目 ID
PROBLEM_2025_A = 2097509647494889474  # Testing Time: The Constant Wear On Stairs
PROBLEM_2025_B = 2097509740369362945  # Managing Sustainable Tourism
PROBLEM_2025_C = 2097509845520564225  # Models for Olympic Medal Tables
PROBLEM_2025_D = 2097509961371435009  # A Roadmap to a Better City
PROBLEM_2025_E = 2097510037481275393  # Making Room for Agriculture

def quote(value: str | None) -> str:
    if value is None:
        return "NULL"
    return "'" + value.replace("\\", "\\\\").replace("'", "''") + "'"


def main() -> None:
    # 固定数据中心和节点生成确定性雪花 ID
    sf = Snowflake(datacenter_id=1, worker_id=2)

    team_ids = sf.next_ids(5)
    member_ids = sf.next_ids(8)
    recruitment_ids = sf.next_ids(6)
    application_ids = sf.next_ids(6)

    teams = [
        (team_ids[0], "星河楼梯建模队", "正在招募建模与论文方向队友，专注于 2025 MCM A 题楼梯磨损反演。", 1002, PROBLEM_2025_A,
         1, "PREPARING", None, None, None),
        (team_ids[1], "朱诺生态旅游实战队", "三类职责齐全，正在进行 2025 MCM B 题朱诺可持续旅游限时练习。", 1002, PROBLEM_2025_B,
         1, "IN_PROGRESS", "2026-08-25 08:00:00", "2026-08-28 08:00:00", None),
        (team_ids[2], "奥运奖牌时序复盘队", "已完成 2025 MCM C 题奥运奖牌榜练习，用于查看结束状态和历史成员。", 1006, PROBLEM_2025_C,
         1, "ENDED", "2026-08-15 09:00:00", "2026-08-18 09:00:00", "2026-08-18 08:36:00"),
        (team_ids[3], "城市路网历史队", "组建阶段主动解散，保留 2025 ICM D 题招募与申请历史。", 1008, PROBLEM_2025_D,
         0, "DISBANDED", None, None, "2026-08-12 18:20:00"),
        (team_ids[4], "农业生态演替小组", "队伍广场中的公开招募示例，演示当前用户针对 2025 ICM E 题的待审核申请。", 1005, PROBLEM_2025_E,
         1, "PREPARING", None, None, None),
    ]

    members = [
        (member_ids[0], team_ids[0], 1002, "leader", 0, 1, 0, 1),
        (member_ids[1], team_ids[1], 1002, "leader", 1, 0, 0, 1),
        (member_ids[2], team_ids[1], 1005, "member", 0, 1, 1, 1),
        (member_ids[3], team_ids[2], 1006, "leader", 1, 0, 0, 1),
        (member_ids[4], team_ids[2], 1002, "member", 0, 1, 0, 1),
        (member_ids[5], team_ids[2], 1007, "member", 0, 0, 1, 0),
        (member_ids[6], team_ids[3], 1008, "leader", 1, 1, 1, 1),
        (member_ids[7], team_ids[4], 1005, "leader", 1, 0, 0, 1),
    ]

    recruitments = [
        (recruitment_ids[0], team_ids[0], 1, 0, 0, "OPEN"),
        (recruitment_ids[1], team_ids[0], 0, 0, 1, "OPEN"),
        (recruitment_ids[2], team_ids[1], 0, 1, 1, "FILLED"),
        (recruitment_ids[3], team_ids[2], 0, 0, 1, "CLOSED"),
        (recruitment_ids[4], team_ids[3], 1, 0, 0, "CLOSED"),
        (recruitment_ids[5], team_ids[4], 0, 1, 1, "OPEN"),
    ]

    applications = [
        (application_ids[0], team_ids[0], recruitment_ids[0], 1003, "擅长偏微分方程数值解与反问题，希望负责建模。", "pending", 1, None, None),
        (application_ids[1], team_ids[0], recruitment_ids[1], 1004, "有 LaTeX 论文排版和 Origin/Matplotlib 可视化经验。", "pending", 1, None, None),
        (application_ids[2], team_ids[1], recruitment_ids[2], 1005, "熟悉 Python 运筹优化与 Gurobi/PuLP 建模。", "approved", None, 1002, "2026-08-24 19:30:00"),
        (application_ids[3], team_ids[2], recruitment_ids[3], 1007, "希望承担论文写作与图表设计职责。", "approved", None, 1006, "2026-08-14 20:10:00"),
        (application_ids[4], team_ids[4], recruitment_ids[5], 1002, "希望参与农业生态演替模型与生物链仿真练习。", "pending", 1, None, None),
        (application_ids[5], team_ids[0], recruitment_ids[0], 1009, "申请参与微元几何建模。", "rejected", None, 1002, "2026-08-24 21:15:00"),
    ]

    team_values = [
        f"({id_}, {quote(name)}, {quote(description)}, {leader_id}, {problem_id}, {status}, "
        f"{quote(practice_status)}, {quote(started_at)}, {quote(deadline_at)}, {quote(ended_at)}, NOW(), NOW(), 0)"
        for id_, name, description, leader_id, problem_id, status, practice_status, started_at, deadline_at, ended_at in teams
    ]

    member_values = [
        f"({id_}, {team_id}, {user_id}, '{role}', {modeler}, {programmer}, {writer}, {can_submit}, NOW(), NOW())"
        for id_, team_id, user_id, role, modeler, programmer, writer, can_submit in members
    ]

    recruitment_values = [
        f"({id_}, {team_id}, {modeler}, {programmer}, {writer}, '{status}', NOW(), NOW())"
        for id_, team_id, modeler, programmer, writer, status in recruitments
    ]

    application_values = [
        f"({id_}, {team_id}, {recruitment_id}, {applicant_id}, {quote(message)}, '{status}', "
        f"{pending_marker if pending_marker is not None else 'NULL'}, "
        f"{handled_by if handled_by is not None else 'NULL'}, {quote(handled_at)}, NOW())"
        for id_, team_id, recruitment_id, applicant_id, message, status, pending_marker, handled_by, handled_at in applications
    ]

    team_id_str = ", ".join(str(t) for t in team_ids)

    sql = f"""-- ==================== 当前团队功能演示数据 ====================
-- 由 LeetModel-mock/scripts/generate_team_service_demo.py 生成
-- 关联 2025 美赛真实赛题 ID (A-E)，主键统一使用标准雪花算法 ID
-- vip_demo（用户 1002）覆盖组建中、练习中、练习结束和待审核申请场景

DELETE FROM `team_join_application` WHERE `team_id` IN ({team_id_str});
DELETE FROM `team_recruitment` WHERE `team_id` IN ({team_id_str});
DELETE FROM `team_member` WHERE `team_id` IN ({team_id_str});
DELETE FROM `team` WHERE `id` IN ({team_id_str});

INSERT INTO `team`
(`id`, `name`, `description`, `leader_id`, `problem_id`, `status`, `practice_status`, `started_at`, `deadline_at`, `ended_at`, `create_time`, `update_time`, `deleted`)
VALUES
{',\n'.join(team_values)};

INSERT INTO `team_member`
(`id`, `team_id`, `user_id`, `role`, `modeler`, `programmer`, `writer`, `can_submit`, `joined_at`, `create_time`)
VALUES
{',\n'.join(member_values)};

INSERT INTO `team_recruitment`
(`id`, `team_id`, `need_modeler`, `need_programmer`, `need_writer`, `status`, `create_time`, `update_time`)
VALUES
{',\n'.join(recruitment_values)};

INSERT INTO `team_join_application`
(`id`, `team_id`, `recruitment_id`, `applicant_id`, `message`, `status`, `pending_marker`, `handled_by`, `handled_at`, `create_time`)
VALUES
{',\n'.join(application_values)};
"""
    OUTPUT_PATH.write_text(sql, encoding="utf-8")
    print(f"已生成 {len(teams)} 支交互演示队伍、{len(members)} 条成员关系、{len(recruitments)} 个招募位置和 {len(applications)} 条申请 (雪花 ID 驱动)")
    print(f"输出文件：{OUTPUT_PATH}")


if __name__ == "__main__":
    main()
