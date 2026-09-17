# @SpringBootApplication 为什么扫不到 common 模块的 Bean？

> 日期：2026-07-26
> 相关模块：common-core, common-security, user

---

## 问题

user 服务启动后，`/api/auth/register` 返回 401，Knife4j 弹登录框——明明 `SecurityConfig` 里配了 `permitAll()`。进一步排查发现：`SecurityConfig`、`GlobalExceptionHandler`、`AuthExceptionHandler` 等 common 模块的 Bean **根本没被加载**。

## 根因

`@SpringBootApplication` 的默认组件扫描范围是**启动类所在包及其子包**：

```java
// user 服务的启动类
package com.leetmodel.user;

@SpringBootApplication  // ← 默认只扫描 com.leetmodel.user 及子包
public class LeetModelUserApplication { ... }
```

而 common 模块的 Bean 在 `com.leetmodel.common.*` 下，与 `com.leetmodel.user` 是**平级包**，不在扫描范围内。

```
com.leetmodel
├── common          ← 没被扫到 ❌
│   ├── core.exception.GlobalExceptionHandler
│   └── security.config.SecurityConfig
├── user            ← 被扫到 ✅
│   └── controller.AuthController
└── leetmodelproblem
```

## 修复

将扫描范围提升到公共父包 `com.leetmodel`：

```java
@SpringBootApplication(scanBasePackages = "com.leetmodel")
```

这样 `com.leetmodel.common`、`com.leetmodel.user`、`com.leetmodel.leetmodelproblem` 全部被覆盖。

## 关键认知

- **`@SpringBootApplication`** = `@Configuration` + `@EnableAutoConfiguration` + `@ComponentScan`
- `@ComponentScan` 默认扫描**当前类所在包及子包**，不包含平级包
- 多模块项目中，每个业务模块的启动类都在各自的子包下，需要显式指定 `scanBasePackages`
- 另一个方案是用 `@Import` 逐个导入，但多模块项目统一用 `scanBasePackages` 更简洁

## 面试可讲点

> "Spring Boot 的自动扫描默认只在启动类所在包及其子包。多模块项目里 common 模块是平级包，不会被扫到。解决方式是在 @SpringBootApplication 上通过 scanBasePackages 指定公共父包。不推荐用 @Import 一个个导——模块多了不好维护。"
