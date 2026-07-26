# user 服务设计

> 创建日期：2026-07-26
> 状态：已实现

---

## 一、职责

用户服务（端口 8081）：注册登录、RBAC 五表权限管理、为 common-security 提供角色权限数据。

---

## 二、包结构

采用标准分层结构：controller（对外 API + 内部 Feign 实现）、service（认证/用户/角色）、mapper（MyBatis-Plus BaseMapper）、entity（5 个实体）、dto（请求/响应对象）、enums（模块错误码）。数据库迁移脚本位于 `resources/db/migration/`。

---

## 三、RBAC 五表

```
user (1) ── (N) user_role (N) ── (1) role (1) ── (N) role_permission (N) ── (1) permission
```

### 初始数据

| 角色 | 编码 | 拥有权限 |
|------|------|---------|
| 管理员 | admin | user:read, user:update, submission:create, suggestion:create |
| VIP | vip | submission:create, suggestion:create |
| 普通用户 | user | submission:create |

---

## 四、API 接口

| 方法 | 路径 | 说明 | 认证 |
|------|------|------|------|
| POST | `/api/auth/register` | 注册 | 无需 |
| POST | `/api/auth/login` | 登录 → 返回 JWT | 无需 |
| POST | `/api/auth/logout` | 登出 → Token 加入黑名单 | 需登录 |
| GET | `/internal/users/{userId}/roles` | 查询角色权限（内部 Feign） | 内部 |

---

## 五、关键技术点

- **Flyway**：数据库版本管理，V1=user 表，V2=RBAC 四表 + 初始数据
- **BCrypt**：密码加密，Spring Security 内置
- **注册时自动分配 user 角色**：user_role 表插入 `(userId, 3)`
- **common-security 依赖 common-api 声明接口，user 服务实现接口**：`InternalUserController` 路径匹配 `UserFeignClient` 的声明
