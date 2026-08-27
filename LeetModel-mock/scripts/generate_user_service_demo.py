from __future__ import annotations

import json
from pathlib import Path
from urllib.request import urlopen

# ==================== 场景配置 ====================
MOCK_API_BASE = "http://127.0.0.1:8000"
DEFAULT_PASSWORD = "123456"
SEED = 20260822
GENERATED_COUNT = 10
USER_ID_START = 1001

FIXED_ACCOUNTS = [
    {
        "id": 1001,
        "username": "admin",
        "nickname": "系统管理员",
        "email": "admin@leetmodel.local",
        "role_id": 1,
        "status": 1,
    },
    {
        "id": 1002,
        "username": "vip_demo",
        "nickname": "演示VIP用户",
        "email": "vip@leetmodel.local",
        "role_id": 2,
        "status": 1,
    },
]

ROOT = Path(__file__).resolve().parent.parent.parent
OUTPUT_PATH = (
    ROOT
    / "LeetModel-backend/user-service/src/main/resources/db/migration/"
    / "V3__insert_mock_users.sql"
)


def fetch_json(path: str) -> list:
    with urlopen(f"{MOCK_API_BASE}{path}") as response:
        body = json.loads(response.read().decode("utf-8"))
    return body["data"]


def sql_escape(value: str) -> str:
    return value.replace("\\", "\\\\").replace("'", "''")


def main() -> None:
    # 1. 通过通用 mock 接口分别获取各类数据
    generated_ids = list(range(USER_ID_START + len(FIXED_ACCOUNTS), USER_ID_START + len(FIXED_ACCOUNTS) + GENERATED_COUNT))
    usernames = fetch_json(
        f"/api/v1/text/usernames?count={GENERATED_COUNT}&min_length=4&max_length=16&seed={SEED}"
    )
    nicknames = fetch_json(
        f"/api/v1/names/chinese?count={GENERATED_COUNT}&min_length=2&max_length=3&seed={SEED}"
    )
    emails = fetch_json(f"/api/v1/text/emails?count={GENERATED_COUNT}&seed={SEED}")
    avatars = fetch_json(f"/api/v1/avatars?count={GENERATED_COUNT + len(FIXED_ACCOUNTS)}&style=micah&seed={SEED}")
    bcrypt_hashes = fetch_json(
        f"/api/v1/passwords/bcrypt?count={GENERATED_COUNT + len(FIXED_ACCOUNTS)}&password={DEFAULT_PASSWORD}"
    )

    # 2. 组装用户与角色绑定数据
    users_sql: list[str] = []
    user_role_sql: list[str] = []
    user_role_id = 1

    for index in range(GENERATED_COUNT + len(FIXED_ACCOUNTS)):
        if index < len(FIXED_ACCOUNTS):
            account = FIXED_ACCOUNTS[index]
            user_id = account["id"]
            username = account["username"]
            nickname = account["nickname"]
            email = account["email"]
            role_id = account["role_id"]
            status = account["status"]
        else:
            offset = index - len(FIXED_ACCOUNTS)
            user_id = generated_ids[offset]
            username = usernames[offset]
            nickname = nicknames[offset]
            email = emails[offset]
            role_id = 3
            status = 0 if offset == GENERATED_COUNT - 1 else 1

        password_hash = bcrypt_hashes[index]["hash"]
        avatar_url = avatars[index]

        users_sql.append(
            f"({user_id}, '{sql_escape(username)}', '{password_hash}', "
            f"'{sql_escape(nickname)}', '{sql_escape(email)}', '{avatar_url}', "
            f"{status}, NOW(), NOW(), 0)"
        )
        user_role_sql.append(f"({user_role_id}, {user_id}, {role_id})")
        user_role_id += 1

    sql = (
        "-- ==================== 用户演示数据 ====================\n"
        "-- 由 LeetModel-mock/scripts/generate_user_service_demo.py 生成\n"
        f"-- 统一演示密码：{DEFAULT_PASSWORD}\n"
        "-- 角色：admin=1, vip=2, user=3\n"
        "-- 状态：1=正常, 0=禁用\n\n"
        "INSERT INTO user (id, username, password, nickname, email, avatar_url, status, create_time, update_time, deleted)\n"
        "VALUES\n"
        + ",\n".join(users_sql)
        + "\nON DUPLICATE KEY UPDATE username = VALUES(username);\n\n"
        "INSERT INTO user_role (id, user_id, role_id)\n"
        "VALUES\n"
        + ",\n".join(user_role_sql)
        + "\nON DUPLICATE KEY UPDATE role_id = VALUES(role_id);\n"
    )

    OUTPUT_PATH.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT_PATH.write_text(sql, encoding="utf-8")
    print(f"已生成 {len(users_sql)} 个演示用户，统一密码：{DEFAULT_PASSWORD}")
    print(f"输出文件：{OUTPUT_PATH}")


if __name__ == "__main__":
    main()
