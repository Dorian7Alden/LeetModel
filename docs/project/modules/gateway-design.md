# gateway 服务设计


> 创建日期：2026-07-26
> 状态：已实现


## 一、职责

API 网关（端口 8080）：系统唯一对外入口。统一路由转发、JWT 鉴权拦截、跨域处理。业务服务不直接暴露给前端。


## 二、路由规则

| 匹配路径 | 转发目标 | 鉴权 |
|---------|---------|------|
| `/api/auth/login` | user 服务 (`lb://leetmodel-user`) | 白名单 |
| `/api/auth/register` | user 服务 (`lb://leetmodel-user`) | 白名单 |
| `/api/user/**` | user 服务 (`lb://leetmodel-user`) | 需登录 |

后续服务（team、problem 等）上线后，追加对应路由规则。路由使用 `lb://` 前缀从 Nacos 拉取实例列表，不做 IP:端口硬编码。


## 三、鉴权架构

采用 Sa-Token JWT 无状态模式：Gateway 校验 JWT 签名和过期时间，不查 Redis Session。白名单路径在 `SaReactorFilter.addExclude()` 中声明。校验失败返回统一 JSON 格式 `{code: 40100, message: "未登录或 Token 已失效"}`。

JWT 密钥必须与 user 服务保持一致，否则 user 服务签发的 Token 会被网关拒绝。


## 四、跨域处理

通过 `CorsWebFilter` 全局处理 CORS。Gateway 基于 WebFlux 响应式架构，不能用 Spring MVC 的 `CorsConfigurationSource`。


## 五、关键技术点

- **Sa-Token 响应式变体**：Gateway 基于 WebFlux，必须使用 `sa-token-reactor-spring-boot3-starter`，servlet 版无法启动。
- **依赖排除**：`common-core` 传递了 `spring-boot-starter-web` 和 `mybatis-plus-spring-boot3-starter`，Gateway 必须排除这两个依赖并禁用 DataSource 自动配置。
- **当前未接入 Redis 黑名单**：登出后 Token 在过期前仍可通过网关。业务服务层做二次校验，后续可接入 Redis 完善。
