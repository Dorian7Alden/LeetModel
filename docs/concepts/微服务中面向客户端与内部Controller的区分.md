# 微服务中面向客户端与内部 Controller 的区分

> 日期：2026-08-22
> 相关模块：各微服务的 controller 包

---

## 问题

微服务中一个服务可能既要给前端客户端提供接口，也要给其他微服务通过 Feign 提供接口。这两类 Controller 应该怎么组织？要不要分成 client、internal、admin 等不同目录？

## 结论

接口只分两类：面向客户端的接口和面向微服务内部调用的接口。目录上不强行拆分，统一放在 controller 包下，通过命名和路径区分：

- 面向客户端的 Controller：直接放在 controller 包下，接口路径使用 `/api/**`
- 面向 Feign 的 Controller：命名使用 `Internal` 关键词，接口路径使用 `/internal/**`

不需要再按 client、internal、admin 等维度建立子文件夹，避免不必要的分层。

## 两类接口的区别

| 维度 | 面向客户端的 Controller | 面向 Feign 的 Internal Controller |
|---|---|---|
| 调用方 | 前端客户端，经过网关 | 其他微服务，Feign 直连 |
| 路径前缀 | `/api/**` | `/internal/**` |
| 命名特征 | AuthController、UserController | InternalUserController、InternalTeamController |
| 鉴权方式 | Sa-Token 登录态、@SaCheckRole、@SaCheckPermission | 不直接对客户端开放，通过 Feign 契约约束 |
| 契约来源 | 前端与后端的接口约定 | common-api 中的 @FeignClient 声明 |

## 为什么内部接口要独立

内部接口的调用方是其他服务，不是用户。混在对外接口里会带来三个问题：

1. 对外接口带用户级鉴权，Feign 调用不容易携带登录态，会被拦截。
2. 对外接口受网关路由和前端契约影响，内部调用需要的是稳定的服务间契约。
3. 内部接口如果暴露给客户端，会扩大攻击面，违反最小暴露原则。

因此内部接口单独使用 `/internal/**` 路径前缀，并通过 `InternalXxxController` 命名区分。

## 为什么不按目录拆分

目录拆分的本质是按技术维度分层，但 controller 包本身已经是接口层，继续拆出 client、internal、admin 子目录收益很低：

- 一个包下的 Controller 数量并不多，通过命名即可快速识别
- 内部接口用 Internal 关键词已经足够清晰
- admin 接口本质也是面向客户端的接口，只是权限要求不同，通过 @SaCheckRole 体现即可
- 每多一层目录，包名变长，移动文件成本增加，但可读性提升有限

因此选择减少分层，全部 Controller 放在同一个包下。

## 项目中的约定

```text
user-service/src/main/java/com/leetmodel/user/controller/
├── AuthController.java
├── UserController.java
├── AdminUserController.java
├── PermissionController.java
├── RoleController.java
└── InternalUserController.java
```

- AuthController：登录、注册、登出
- UserController：当前用户个人信息管理
- AdminUserController、PermissionController、RoleController：管理员视角的客户端接口，通过 @SaCheckRole("admin") 控制
- InternalUserController：给 common-security 提供角色权限数据的 Feign 接口

## 面试可讲点

> "微服务里接口分两类：对外接口走网关，面向客户端；内部接口走 Feign，面向服务间调用。我用 /api 和 /internal 两套路径前缀区分，内部接口用 Internal 命名，不暴露给客户端。目录上不做过多拆分，controller 包统一管理，避免为了分层而分层。管理员接口不是单独的一层，它本质还是客户端接口，只是通过 @SaCheckRole 做权限控制。"
