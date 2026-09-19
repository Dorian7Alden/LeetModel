# 自动配置引用可选依赖导致无 Redis 服务启动失败

> 影响范围：common-core 新增的自动配置、未引入 Redis 的服务（ai-gateway-service 等）

## 报错现象

Token 黑名单的 Redis 超时配置放进 `common-core` 自动配置后，本地受影响的模块测试仍能通过，但全量 Reactor 测试在 `ai-gateway-service` 集成测试上失败，`ApplicationContext` 无法加载：

```text
IllegalStateException: Failed to introspect Class
  [com.leetmodel.common.core.security.TokenBlacklistRedisConfiguration]
Caused by: NoClassDefFoundError:
  org/springframework/data/redis/connection/lettuce/LettuceClientConfiguration$LettuceClientConfigurationBuilder
Caused by: ClassNotFoundException:
  org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration$LettuceClientConfigurationBuilder
```

## 根因分析

`TokenBlacklistRedisConfiguration` 的 Bean 方法返回 `LettuceClientConfigurationBuilderCustomizer`，该接口的方法签名引用 `LettuceClientConfiguration.LettuceClientConfigurationBuilder`（spring-data-redis）。`ai-gateway-service` 没有引入 Redis Starter，类加载器解析配置类时找不到该类型，于是在条件判断阶段就抛出 `NoClassDefFoundError`。

只在配置类上写 `@ConditionalOnClass(LettuceClientConfigurationBuilderCustomizer.class)` 不能解决问题：类名条件虽然可以不加载目标类，但条件所在的外层类本身在条件评估期间已被内省，签名里的缺失类型同样会触发失败。

## 修复方案

采用“外层类零可选依赖 + 嵌套条件配置”的写法：

```java
@AutoConfiguration
public class TokenBlacklistRedisConfiguration {

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory")
    static class LettuceTimeoutConfiguration {
        @Bean
        LettuceClientConfigurationBuilderCustomizer tokenBlacklistRedisTimeoutCustomizer(...) { ... }
    }
}
```

- 外层类不出现任何 Redis 或 Lettuce 类型，缺依赖的服务可以安全加载并直接跳过。
- 内层用**类名字符串**条件判断，避免条件评估阶段解析缺失类型。
- 需要编译期类型时，把对应 Starter 声明为 `optional` 依赖。

## 回归验证

- `ai-gateway-service` 模块测试恢复通过（95 项，4 项门禁跳过）。
- 后端根 Reactor 全量测试 22 个模块全部成功：1004 项、零失败、26 项外部门禁按设计跳过。
- 引入 Redis 的网关与业务服务仍正常应用 300 毫秒黑名单超时（黑名单命中与停机降级行为不变）。

## 经验

- 公共模块的自动配置一旦引用可选依赖类型，就会把“可选”变成所有服务的启动前置条件，必须在结构上隔离。
- 受影响的只有未引入该依赖的服务，因此问题往往在单模块测试中不可见，只有全量 Reactor 或真实启动才暴露。
- 新增自动配置后，至少要在“有依赖”和“无依赖”两类服务上各验证一次。
