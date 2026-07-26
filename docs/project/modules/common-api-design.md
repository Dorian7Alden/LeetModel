# common-api 公共模块设计

> 创建日期：2026-07-26
> 状态：已实现

---

## 一、目标

构建 Feign 接口声明模块 `common-api`（jar 包，非独立服务），定义跨服务调用的接口契约和降级策略。各服务通过依赖此模块获取类型安全的 Feign 客户端，无需手动维护 URL 和 DTO。

---

## 二、组件设计

### 2.1 UserRoleDTO — 角色权限传输对象

封装 userId、角色列表和权限列表，用于 common-security 从 user 服务查询当前用户的 RBAC 数据。

### 2.2 UserFeignClient — 用户服务 Feign 接口

声明 `@FeignClient("leetmodel-user")` 调用用户服务的内部端点，获取角色权限信息。

### 2.3 UserFeignFallback — 降级策略

实现 `FallbackFactory`，在 user 服务不可用时返回空的角色权限列表。由 common-security 的 `AuthExceptionHandler` 将空角色转换为 403 响应，确保安全不倒置。

---

## 三、依赖关系

- 依赖 `common-core`（使用统一响应体）
- 依赖 `spring-cloud-starter-openfeign`（提供 @FeignClient 声明能力）
- 被 `common-security` 和 `user` 服务依赖

---

## 四、设计决策

- **契约先行**：接口声明在 common-api，实现在 user 服务，调用方通过接口而非直接 HTTP 调用
- **安全降级**：user 服务不可用时拒绝访问（403），而非放行——安全不倒置
