# Problem 服务启动报 UserFeignClient Bean 缺失

---

## 报错现象

```text
APPLICATION FAILED TO START

Description:

Parameter 0 of constructor in com.leetmodel.common.security.handler.StpInterfaceImpl
required a bean of type 'com.leetmodel.common.api.feign.UserFeignClient' that could not be found.
```

Problem 服务启动时 Spring 容器初始化失败，提示 `StpInterfaceImpl` 构造器需要的 `UserFeignClient` Bean 不存在。

---

## 根因分析

`common-security` 模块中的 `StpInterfaceImpl` 标注了 `@Component`，其构造器注入 `UserFeignClient`（来自 `common-api` 模块，通过 `@FeignClient` 声明）。

Problem 服务在 POM 中引入了 `common-security`，Spring 组件扫描将其 `StpInterfaceImpl` 加载到容器中。但 Problem 服务没有引入 `common-api`，也没有启用 `@EnableFeignClients`，导致 `UserFeignClient` 的代理 Bean 未创建。

`StpInterfaceImpl` 的设计意图是给需要 RBAC 鉴权的服务使用（如 user 服务，既有 common-security 又有 common-api + Feign 配置）。Problem 服务不需要运行时鉴权，只需要 `common-core` 中的 `ErrorCode` 接口来定义业务错误码。

---

## 修复方案

从 Problem 服务的 POM 中移除 `common-security` 依赖。`ErrorCode` 接口定义在 `common-core` 中，不需要 common-security。

```xml
<!-- 只需要 common-core，不需要 common-security -->
<dependency>
    <groupId>com.leetmodel</groupId>
    <artifactId>common-core</artifactId>
</dependency>
```

---

## 知识点

**按需引入模块依赖**：微服务中，common 模块不是无条件全部引用的。`common-security` 内含 Sa-Token 鉴权实现，依赖 `UserFeignClient`——只有需要服务内鉴权的微服务（如 user）才引入它。不需要鉴权的服务引入 `common-core` 就够。

**鉴权放在 Gateway 层**：对于 Problem 这类简单业务服务，不需要在服务内部做 Sa-Token 角色/权限校验。鉴权统一在 Gateway 层处理，服务内部专注于业务逻辑。
