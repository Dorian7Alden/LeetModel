# LeetModel-mock

提供业务无关的 mock 数据 API 服务。各类基础数据由统一接口生成，场景脚本按需调用接口组装出表级演示数据。

## 为什么需要程序生成

用户密码使用 BCrypt 加密存储，AI 直接生成 SQL 无法保证密码可登录。本项目用 bcrypt 对统一演示密码进行真实加密，Faker 生成高质量的姓名、用户名、邮箱等基础数据。

## 项目结构

```
LeetModel-mock/
├── app/
│   ├── main.py                  # FastAPI 入口
│   ├── generators.py            # 基础数据生成器
│   └── routers/
│       ├── names.py             # 中英文名字接口
│       ├── numbers.py           # 随机数、ID 接口
│       ├── dates.py             # 日期时间接口
│       ├── texts.py             # 用户名、邮箱、词句接口
│       ├── passwords.py         # 明文密码、BCrypt 哈希接口
│       └── misc.py              # 头像、URL、手机号、布尔值接口
├── scripts/
│   ├── generate_user_service_demo.py        # 场景脚本：组装 user-service 演示数据
│   ├── generate_team_service_demo.py        # 场景脚本：组装 team-service 演示数据
│   ├── generate_problem_service_demo.py     # 场景脚本：组装 problem-service 题库演示数据
│   └── generate_submission_service_demo.py  # 场景脚本：组装提交、评审与排行榜全量演示数据
├── requirements.txt
└── README.md
```

## 启动 mock 服务

```bash
cd LeetModel-mock
pip install -r requirements.txt
uvicorn app.main:app --host 0.0.0.0 --port 8000
```

启动后访问：

- 接口文档：`http://127.0.0.1:8000/docs`
- 健康检查：`http://127.0.0.1:8000/health`

## 基础数据接口

所有接口返回统一格式：

```json
{"code": 20000, "message": "success", "data": []}
```

### 名字

| 接口 | 参数 |
|---|---|
| `GET /api/v1/names/chinese` | `count`、`min_length`、`max_length`、`seed` |
| `GET /api/v1/names/english` | `count`、`min_length`、`max_length`、`seed` |

### 数字

| 接口 | 参数 |
|---|---|
| `GET /api/v1/numbers/integers` | `count`、`min`、`max`、`seed` |
| `GET /api/v1/numbers/decimals` | `count`、`min`、`max`、`precision`、`seed` |
| `GET /api/v1/numbers/ids` | `start`、`count` |

### 日期时间

| 接口 | 参数 |
|---|---|
| `GET /api/v1/dates` | `count`、`start`、`end`、`seed` |
| `GET /api/v1/dates/datetimes` | `count`、`start`、`end`、`seed` |

### 文本

| 接口 | 参数 |
|---|---|
| `GET /api/v1/text/usernames` | `count`、`min_length`、`max_length`、`seed` |
| `GET /api/v1/text/emails` | `count`、`seed` |
| `GET /api/v1/text/words` | `count`、`min_length`、`max_length`、`locale`、`seed` |
| `GET /api/v1/text/sentences` | `count`、`min_length`、`max_length`、`locale`、`seed` |

### 密码

| 接口 | 参数 |
|---|---|
| `GET /api/v1/passwords/plain` | `count`、`length`、`seed` |
| `GET /api/v1/passwords/bcrypt` | `count`、`password` |

### 其他

| 接口 | 参数 |
|---|---|
| `GET /api/v1/avatars` | `count`、`style`、`seed` |
| `GET /api/v1/urls` | `count`、`seed` |
| `GET /api/v1/phone-numbers` | `count`、`seed` |
| `GET /api/v1/booleans` | `count`、`seed` |

## 请求示例

```bash
# 生成 5 个中文名字
curl "http://127.0.0.1:8000/api/v1/names/chinese?count=5&min_length=2&max_length=3"

# 生成 3 个 18 到 60 之间的随机整数
curl "http://127.0.0.1:8000/api/v1/numbers/integers?count=3&min=18&max=60"

# 生成 5 个日期
curl "http://127.0.0.1:8000/api/v1/dates?count=5&start=2024-01-01&end=2024-12-31"

# 生成 10 个 BCrypt 密码哈希，明文为 123456
curl "http://127.0.0.1:8000/api/v1/passwords/bcrypt?count=10&password=123456"
```

响应格式统一为：

```json
{
  "code": 20000,
  "message": "success",
  "data": ["数据1", "数据2"]
}
```

## 生成场景数据

以 user-service 演示数据为例：

```bash
# 先启动 mock 服务，再执行场景脚本
python3 scripts/generate_user_service_demo.py
```

脚本会调用上述基础接口获取用户名、中文名、邮箱、头像、BCrypt 密码哈希，然后组装为 Flyway 迁移脚本：

```text
LeetModel-backend/user-service/src/main/resources/db/migration/V3__insert_mock_users.sql
```

生成后启动 user-service，Flyway 会自动执行 V3 脚本插入演示数据。

team-service 演示数据复用 user-service V3 中的正常用户：

```bash
python3 scripts/generate_team_service_demo.py
```

脚本基于当前 team-service 表结构生成组建中、练习中、练习结束、已解散、开放招募位置及申请审核场景，并输出：

```text
LeetModel-backend/team-service/src/main/resources/db/migration/V8__refresh_team_demo_data.sql
```

使用 `vip_demo` 登录后，可以同时看到自己管理的组建中与练习中队伍、自己加入的练习结束队伍，以及在队伍广场中的待审核申请。

problem-service 演示数据使用固定业务场景覆盖赛事、年份、语言、难度、三类标签组合、历史分数区间和草稿隔离：

```bash
python3 scripts/generate_problem_service_demo.py
```

脚本输出：

```text
LeetModel-backend/problem-service/src/main/resources/db/migration/V6__insert_mock_problems.sql
```

submission-service、ai-review-service 与 ranking-service 演示数据覆盖多版本提交、最终稿锁定、全分段（0-59、60-69、70-79、80-89、90-100）评审结果与当前排行榜快照：

```bash
python3 scripts/generate_submission_service_demo.py
```

脚本输出：

```text
LeetModel-backend/user-service/src/main/resources/db/migration/V6__insert_more_mock_users.sql
LeetModel-backend/team-service/src/main/resources/db/migration/V11__insert_leaderboard_demo_teams.sql
LeetModel-backend/submission-service/src/main/resources/db/migration/V2__insert_mock_submissions.sql
LeetModel-backend/ai-review-service/src/main/resources/db/migration/V3__insert_mock_reviews.sql
LeetModel-backend/ranking-service/src/main/resources/db/migration/V2__insert_mock_rankings.sql
```

## 演示账号

| 用户名 | 密码 | 角色 |
|---|---|---|
| admin | 123456 | 管理员 |
| vip_demo | 123456 | VIP 用户 |
| 其余生成账号 | 123456 | 普通用户 |
