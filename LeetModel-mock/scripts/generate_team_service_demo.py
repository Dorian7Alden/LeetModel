from __future__ import annotations

from pathlib import Path

# 引用 user-service V3 的可用用户和 problem-service V6 的已发布题目。
TEAMS = [
    (2001, "星河建模队", "正在招募建模与论文方向队友，适合验证招募位置和申请审核。", 1002, 51008,
     1, "PREPARING", None, None, None),
    (2002, "应急优化实战队", "三类职责齐全，正在进行应急物资配送限时练习。", 1002, 51007,
     1, "IN_PROGRESS", "2026-08-25 08:00:00", "2026-08-28 08:00:00", None),
    (2003, "潮汐调度复盘队", "已完成共享单车调度练习，用于查看结束状态和历史成员。", 1006, 51001,
     1, "ENDED", "2026-08-15 09:00:00", "2026-08-18 09:00:00", "2026-08-18 08:36:00"),
    (2004, "湖泊研究历史队", "组建阶段主动解散，保留成员与招募历史。", 1008, 51002,
     0, "DISBANDED", None, None, "2026-08-12 18:20:00"),
    (2005, "生态承载力小组", "队伍广场中的公开招募示例，演示当前用户待审核申请。", 1005, 51006,
     1, "PREPARING", None, None, None),
]

MEMBERS = [
    (3001, 2001, 1002, "leader", 0, 1, 0, 1),
    (3002, 2002, 1002, "leader", 1, 0, 0, 1),
    (3003, 2002, 1005, "member", 0, 1, 1, 1),
    (3004, 2003, 1006, "leader", 1, 0, 0, 1),
    (3005, 2003, 1002, "member", 0, 1, 0, 1),
    (3006, 2003, 1007, "member", 0, 0, 1, 0),
    (3007, 2004, 1008, "leader", 1, 1, 1, 1),
    (3008, 2005, 1005, "leader", 1, 0, 0, 1),
]

RECRUITMENTS = [
    (4001, 2001, 1, 0, 0, "OPEN"),
    (4002, 2001, 0, 0, 1, "OPEN"),
    (4003, 2002, 0, 1, 1, "FILLED"),
    (4004, 2003, 0, 0, 1, "CLOSED"),
    (4005, 2004, 1, 0, 0, "CLOSED"),
    (4006, 2005, 0, 1, 1, "OPEN"),
]

APPLICATIONS = [
    (5001, 2001, 4001, 1003, "擅长预测模型与特征工程，希望负责建模。", "pending", 1, None, None),
    (5002, 2001, 4002, 1004, "有论文写作和可视化经验。", "pending", 1, None, None),
    (5003, 2002, 4003, 1005, "熟悉 Python 优化算法。", "approved", None, 1002, "2026-08-24 19:30:00"),
    (5004, 2003, 4004, 1007, "希望承担论文职责。", "approved", None, 1006, "2026-08-14 20:10:00"),
    (5005, 2005, 4006, 1002, "希望参与生态承载力评价练习。", "pending", 1, None, None),
    (5006, 2001, 4001, 1009, "申请参与建模。", "rejected", None, 1002, "2026-08-24 21:15:00"),
]

ROOT = Path(__file__).resolve().parent.parent.parent
OUTPUT_PATH = ROOT / "LeetModel-backend/team-service/src/main/resources/db/migration/V8__refresh_team_demo_data.sql"


def quote(value: str | None) -> str:
    if value is None:
        return "NULL"
    return "'" + value.replace("\\", "\\\\").replace("'", "''") + "'"


def main() -> None:
    team_values = []
    for row in TEAMS:
        team_id, name, description, leader_id, problem_id, status, practice_status, started_at, deadline_at, ended_at = row
        team_values.append(
            f"({team_id}, {quote(name)}, {quote(description)}, {leader_id}, {problem_id}, {status}, "
            f"{quote(practice_status)}, {quote(started_at)}, {quote(deadline_at)}, {quote(ended_at)}, NOW(), NOW(), 0)"
        )

    member_values = [
        f"({id_}, {team_id}, {user_id}, '{role}', {modeler}, {programmer}, {writer}, {can_submit}, NOW(), NOW())"
        for id_, team_id, user_id, role, modeler, programmer, writer, can_submit in MEMBERS
    ]
    recruitment_values = [
        f"({id_}, {team_id}, {modeler}, {programmer}, {writer}, '{status}', NOW(), NOW())"
        for id_, team_id, modeler, programmer, writer, status in RECRUITMENTS
    ]
    application_values = [
        f"({id_}, {team_id}, {recruitment_id}, {applicant_id}, {quote(message)}, '{status}', "
        f"{pending_marker if pending_marker is not None else 'NULL'}, "
        f"{handled_by if handled_by is not None else 'NULL'}, {quote(handled_at)}, NOW())"
        for id_, team_id, recruitment_id, applicant_id, message, status, pending_marker, handled_by, handled_at in APPLICATIONS
    ]

    sql = f"""-- ==================== 当前团队功能演示数据 ====================
-- 由 LeetModel-mock/scripts/generate_team_service_demo.py 生成
-- 引用 user-service V3 用户与 problem-service V6 已发布题目
-- vip_demo（用户 1002）覆盖组建中、练习中、练习结束和待审核申请场景

DELETE FROM `team_join_application` WHERE `id` BETWEEN 5001 AND 5099;
DELETE FROM `team_recruitment` WHERE `id` BETWEEN 4001 AND 4099;
DELETE FROM `team_member` WHERE `team_id` BETWEEN 2001 AND 2099;
DELETE FROM `team` WHERE `id` BETWEEN 2001 AND 2099;

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
    print(f"已生成 {len(TEAMS)} 支队伍、{len(MEMBERS)} 条成员关系、{len(RECRUITMENTS)} 个招募位置和 {len(APPLICATIONS)} 条申请")
    print(f"输出文件：{OUTPUT_PATH}")


if __name__ == "__main__":
    main()
