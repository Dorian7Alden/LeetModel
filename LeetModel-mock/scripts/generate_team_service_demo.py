from __future__ import annotations

import json
from pathlib import Path
from urllib.request import urlopen

MOCK_API_BASE = "http://127.0.0.1:8000"
SEED = 20260823

TEAMS = [
    {
        "id": 2001,
        "name": "星河建模队",
        "description": "满员团队，覆盖三类专业角色和成员多角色场景",
        "leader_id": 1002,
        "max_members": 3,
        "status": 1,
        "members": [
            (1002, "leader", True, False, True),
            (1003, "member", False, True, False),
            (1004, "member", False, False, True),
        ],
    },
    {
        "id": 2002,
        "name": "独立思考队",
        "description": "单人团队，专业角色保持默认未选择状态",
        "leader_id": 1005,
        "max_members": 3,
        "status": 1,
        "members": [
            (1005, "leader", False, False, False),
        ],
    },
    {
        "id": 2003,
        "name": "数模探索队",
        "description": "未满员团队，覆盖一人兼任编程手和论文手场景",
        "leader_id": 1006,
        "max_members": 3,
        "status": 1,
        "members": [
            (1006, "leader", True, False, False),
            (1007, "member", False, True, True),
        ],
    },
    {
        "id": 2004,
        "name": "往届挑战队",
        "description": "已解散满员团队，用于验证历史成员数据留存",
        "leader_id": 1008,
        "max_members": 3,
        "status": 0,
        "members": [
            (1008, "leader", True, True, True),
            (1009, "member", True, False, False),
            (1010, "member", False, True, False),
        ],
    },
]

ROOT = Path(__file__).resolve().parent.parent.parent
OUTPUT_PATH = (
    ROOT
    / "LeetModel-backend/team-service/src/main/resources/db/migration/"
    / "V3__insert_mock_teams.sql"
)


def fetch_json(path: str) -> list:
    with urlopen(f"{MOCK_API_BASE}{path}") as response:
        body = json.loads(response.read().decode("utf-8"))
    return body["data"]


def sql_escape(value: str) -> str:
    return value.replace("\\", "\\\\").replace("'", "''")


def main() -> None:
    member_count = sum(len(team["members"]) for team in TEAMS)
    datetimes = sorted(
        fetch_json(
            f"/api/v1/dates/datetimes?count={len(TEAMS) + member_count}"
            f"&start=2026-01-01%2000:00:00&end=2026-08-20%2023:59:59&seed={SEED}"
        )
    )

    team_values: list[str] = []
    member_values: list[str] = []
    member_id = 3001
    datetime_index = 0

    for team in TEAMS:
        team_time = datetimes[datetime_index]
        datetime_index += 1
        member_times = datetimes[datetime_index:datetime_index + len(team["members"])]
        datetime_index += len(team["members"])
        update_time = member_times[-1] if team["status"] == 0 else team_time
        team_values.append(
            f"({team['id']}, '{sql_escape(team['name'])}', "
            f"'{sql_escape(team['description'])}', {team['leader_id']}, "
            f"{team['max_members']}, {team['status']}, '{team_time}', '{update_time}', 0)"
        )

        for member_index, member in enumerate(team["members"]):
            user_id, role, modeler, programmer, writer = member
            joined_at = member_times[member_index]
            member_values.append(
                f"({member_id}, {team['id']}, {user_id}, '{role}', "
                f"{int(modeler)}, {int(programmer)}, {int(writer)}, "
                f"'{joined_at}', '{joined_at}')"
            )
            member_id += 1

    sql = (
        "-- ==================== 团队演示数据 ====================\n"
        "-- 由 LeetModel-mock/scripts/generate_team_service_demo.py 生成\n"
        "-- 用户 ID 引用 user-service V3 演示数据\n"
        "-- 仅使用状态正常的 1002 至 1010，禁用用户 1012 不参与组队\n\n"
        "INSERT INTO `team` "
        "(id, name, description, leader_id, max_members, status, create_time, update_time, deleted)\n"
        "VALUES\n"
        + ",\n".join(team_values)
        + "\nON DUPLICATE KEY UPDATE name = VALUES(name), description = VALUES(description), "
        "leader_id = VALUES(leader_id), max_members = VALUES(max_members), "
        "status = VALUES(status), deleted = VALUES(deleted);\n\n"
        "INSERT INTO team_member "
        "(id, team_id, user_id, role, modeler, programmer, writer, joined_at, create_time)\n"
        "VALUES\n"
        + ",\n".join(member_values)
        + "\nON DUPLICATE KEY UPDATE role = VALUES(role), modeler = VALUES(modeler), "
        "programmer = VALUES(programmer), writer = VALUES(writer), joined_at = VALUES(joined_at);\n"
    )

    OUTPUT_PATH.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT_PATH.write_text(sql, encoding="utf-8")
    print(f"已生成 {len(TEAMS)} 个演示团队和 {member_count} 条成员数据")
    print(f"输出文件：{OUTPUT_PATH}")


if __name__ == "__main__":
    main()
