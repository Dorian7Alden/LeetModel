# Feign 缺少 LoadBalancer 依赖

> 日期：2026-07-26 | 模块：user, common-security

---

## 报错信息

```
BeanCreationException: Error creating bean with name
'com.leetmodel.common.api.feign.UserFeignClient':
FactoryBean threw exception on object creation

Caused by: java.lang.IllegalStateException:
No Feign Client for loadBalancing defined.
Did you forget to include spring-cloud-starter-loadbalancer?
```

## 复现场景

1. `StpInterfaceImpl` 中注入 `UserFeignClient`
2. `UserFeignClient` 用 `@FeignClient(name = "leetmodel-user")` 通过**服务名**调用
3. user 服务启动时，Spring 尝试创建 Feign 代理 → 无法解析服务名 → 报错

## 根因

OpenFeign 通过服务名调用时，需要 **LoadBalancer** 将服务名解析为实际的 IP:端口。Spring Cloud 2020.0 之后，`spring-cloud-starter-netflix-ribbon` 已停更，改用 `spring-cloud-starter-loadbalancer`（Spring Cloud 官方实现）。

但 `spring-cloud-starter-openfeign` **不会自动引入** loadbalancer——需要显式添加。

## 修复

```xml
<!-- common-security/pom.xml 和 LeetModel-user/pom.xml -->
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-loadbalancer</artifactId>
</dependency>
```

## 反思

- Feign 有两种调用方式：① 通过 URL 直连（不需要负载均衡）② 通过服务名（需要）
- 只要 `@FeignClient` 的 `name` 属性指向一个 Nacos 注册的服务名，就必须有 LoadBalancer
- 这个依赖应该放在**使用 Feign 的模块**（common-security），而不是 API 定义模块（common-api）
