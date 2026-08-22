# admin 服务设计

> 创建日期：2026-07-30
> 状态：已实现

---

## 一、职责

管理后台服务通过 Feign 聚合各业务服务的数据，并为管理功能提供面向客户端的统一入口。不拥有自己的数据库，不存储业务数据。

---

## 二、架构模式

```
admin (聚合层, 端口 8084)
  ├── Feign → user (用户统计)
  ├── Feign → problem (题目统计)
  └── Feign → team (团队统计)
```

采用聚合层模式。admin 服务本身无数据库，所有数据通过 Feign 同步调用各业务服务获取。仪表盘查询允许局部降级，管理写操作失败时必须明确返回服务异常。

---

## 三、API 接口

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| GET | `/api/admin/dashboard/stats` | 获取汇总统计数据（用户数/团队数/题目数） | admin |
| GET | `/api/admin/permissions` | 获取权限列表 | admin |
| GET | `/api/admin/permissions/{permissionId}` | 获取权限详情 | admin |
| POST | `/api/admin/permissions` | 创建权限 | admin |
| PUT | `/api/admin/permissions/{permissionId}` | 更新权限 | admin |
| DELETE | `/api/admin/permissions/{permissionId}` | 删除权限 | admin |
| GET | `/api/admin/roles/{roleId}/permissions` | 获取角色权限 | admin |
| PUT | `/api/admin/roles/{roleId}/permissions` | 全量更新角色权限 | admin |

---

## 四、关键设计

- **独立服务不独立数据库**：admin 作为纯聚合层，不持有任何业务数据。这种设计的面试价值：讲清楚"聚合层 vs 独立数据源"的权衡（实时性 vs 解耦 vs 性能）。
- **Feign 降级保护**：DashboardController 通过 `safeGet()` 包装每次 Feign 调用，单个服务异常不影响其他统计项的返回。Fallback 返回 0。
- **后续演进方向**：当服务数量增多、聚合查询变复杂时，可引入定时任务预计算统计结果并缓存，或接入消息队列消费业务事件做准实时统计。
- **RBAC 管理边界**：admin-service 负责管理员鉴权和请求转发，RBAC 数据校验与事务由 user-service 负责。
- **管理接口降级**：服务不可用不能伪装成空数据，避免管理员把故障误判为业务数据为空。
- **权限实际执行**：用户查询接口校验 `user:read`，用户修改接口校验 `user:update`，RBAC 管理接口继续要求 `admin` 角色。
