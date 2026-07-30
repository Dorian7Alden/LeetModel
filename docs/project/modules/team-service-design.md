# team 服务设计

> 创建日期：2026-07-30
> 状态：已实现

---

## 一、职责

团队服务（端口 8082）：创建队伍、成员管理（添加/移除/退出）、解散队伍。数据库 `lm_team`，Flyway 管理表迁移。

---

## 二、数据表

```
team (1) ── (N) team_member (N) ── (1) user（逻辑外键，跨服务）
```

- `team`：继承 BaseEntity，含 name、description、leaderId、maxMembers（默认 3）、status（0=正常/1=已解散）
- `team_member`：不继承 BaseEntity（关联表模式），含 teamId、userId、role（leader/member）、createTime。唯一约束 (team_id, user_id)

---

## 三、API 接口

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| POST | `/api/teams` | 创建队伍，创建人自动成为队长 | 需登录 |
| GET | `/api/teams` | 我加入的队伍列表 | 需登录 |
| GET | `/api/teams/{id}` | 队伍详情（含成员列表） | 需登录 |
| PUT | `/api/teams/{id}` | 更新队伍信息 | 队长 |
| DELETE | `/api/teams/{id}` | 解散队伍 | 队长 |
| POST | `/api/teams/{id}/members` | 添加成员 | 队长 |
| DELETE | `/api/teams/{id}/members/{memberId}` | 移除成员 | 队长 |
| DELETE | `/api/teams/{id}/leave` | 退出队伍 | 队员 |

内部 Feign 接口：
| GET | `/internal/teams/{id}/members` | 查询队伍成员列表 | 内部 |

---

## 四、关键设计

- **成员管理采用直接添加模式**：队长通过 userId 直接添加成员，无需邀请/接受流程。保持基础实现的简洁性。
- **解散为逻辑删除**：team 表走 MyBatis-Plus 逻辑删除（deleted=1），team_member 记录保留，历史可追溯。
- **队长不能退出**：抛出 `LEADER_CANNOT_LEAVE` 错误，需先转让队长或解散。
- **跨服务查询**：通过 `TeamFeignClient` 提供内部接口，供其他服务（如 admin）查询队伍数据。
