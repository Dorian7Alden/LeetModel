# common-security & common-api 公共模块设计

> 创建日期：2026-07-26
> 状态：已确认

---

## 一、目标

构建认证鉴权公共模块 `common-security`（JWT 无状态认证 + RBAC 注解鉴权），以及 Feign 接口声明模块 `common-api`（跨服务调用契约）。

---

## 二、认证 vs 鉴权

| 阶段 | 英文 | 问题 | 实现方式 |
|------|------|------|---------|
| 认证 | Authentication | "你是谁" | JWT 签发 + 验签 + Redis 黑名单 |
| 鉴权 | Authorization | "你能做什么" | @SaCheckRole / @SaCheckPermission 注解 |

流程：登录 → JWT 签发 → 请求携带 Token → 验签确认身份 → 查 RBAC 数据 → 校验角色/权限

---

## 三、模块结构

**common-api**：声明跨服务 Feign 调用契约，包含 `UserRoleDTO`（传输角色权限数据）、`UserFeignClient`（调用 user 服务获取角色权限）以及对应的 `FallbackFactory` 降级实现。

**common-security**：实现认证鉴权核心逻辑，包含 Sa-Token JWT 无状态模式配置、Spring Security 基础配置（关 CSRF + Session）、RBAC 角色枚举、用户上下文工具（`UserContext`）、Feign 调用获取角色权限的 `StpInterfaceImpl`、认证鉴权异常处理器、以及 Token 工具类（login/logout/kickout）。

---

## 四、关键设计决策

### 4.1 JWT 无状态 + Redis 黑名单

- 登录时不存 Redis Session，JWT 签名自验证（轻量、高性能）
- Redis 仅存黑名单——登出/踢人时加入，解决 JWT 无法主动失效的问题
- `StpLogicJwtForStateless(secretKey)` 构造器注入密钥

### 4.2 Feign 降级策略

- user 服务不可用时，`UserFeignFallback` 返回空的角色权限列表
- `AuthExceptionHandler` 会将空角色转换为 403，保证安全不倒置

### 4.3 异常处理分层

- common-core 处理业务异常（`BusinessException` → Result）
- common-security 处理认证鉴权异常（`NotLoginException` → 401, `NotRoleException` → 403）
- 三者互补，不相互覆盖

### 4.4 Sa-Token 1.38.0 适配

- 类名 `SaTokenForJwt` 已移除，改用 `StpLogicJwtForStateless(String secretKey)`
- 行为配置通过 `cn.dev33.satoken.config.SaTokenConfig` 对象注入
- 避免与库内置类冲突，配置类命名为 `SecuritySaTokenConfig`

### 4.5 Redis 缓存基础设施

- `RedisCacheConfig` 配置 `RedisCacheManager`，使 `@Cacheable` / `@CacheEvict` 等 Spring Cache 注解可用
- Key 使用 String 序列化（可读性好），Value 使用 Jackson JSON 序列化
- 序列化时写入类型信息（`activateDefaultTyping`），解决泛型反序列化问题（`List<User>` → `List<LinkedHashMap>`）
- 默认 TTL 30 分钟，TTL 加随机 10% 偏移防缓存雪崩
