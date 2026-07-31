# admin 服务启动报 MybatisPlusConfig 条件装配失败

## 现象

admin 服务启动报错（在排除了 mybatis-plus 依赖之后出现）：

```
java.lang.IllegalStateException: Error processing condition on
com.leetmodel.common.security.config.RedisCacheConfig.cacheManager
Caused by: java.lang.IllegalStateException: Failed to introspect Class
[com.leetmodel.common.core.config.MybatisPlusConfig]
```

在此之前先出现的是 [Gateway 启动报 DataSource 缺失](Gateway启动报DataSource缺失.md) 同款错误（`Failed to configure a DataSource`）——admin 是无数据库服务，但依赖 common-core 传递的 mybatis-plus 触发了 DataSource 自动配置。

## 根因

问题链共三环：

1. **admin 缺 DataSource 排除配置**：admin 与 gateway 同为"无数据库"服务（设计文档明确：纯聚合层，不持有业务数据），但 pom 未排除 common-core 传递的 `mybatis-plus-spring-boot3-starter`，yml 也未禁用 `DataSourceAutoConfiguration`——同类缺陷的漏网之鱼
2. **排除了依赖 → 配置类引用缺失类**：`common-core` 的 `MybatisPlusConfig` 是**无条件 `@Configuration`**，类体内直接引用 `com.baomidou.mybatisplus.*`。依赖被排除后该类仍在 classpath 中（jar 里还有 .class），一旦被加载/introspect 就抛 `NoClassDefFoundError`
3. **扫描放大问题**：admin 启动类 `scanBasePackages = {"com.leetmodel.admin", "com.leetmodel.common"}` 扫到了 `MybatisPlusConfig`，随后 `RedisCacheConfig.cacheManager` 的 `@ConditionalOnBean(RedisConnectionFactory.class)` 评估时需要遍历全部配置类类型 → introspect 失败连锁炸掉

**对照**：gateway 从未报过此错，不是它配置更完善，而是它的启动类**没有** `scanBasePackages`（默认只扫 `com.leetmodel.gateway`），`MybatisPlusConfig` 压根没被注册。admin 扫了 common 包才暴露公共模块的根本缺陷。

## 修复

三步（按问题链由内向外）：

1. **common-core 的根本修复** — `MybatisPlusConfig` 加条件装配保护（公共模块配置类标准做法，同 `MinioConfig` 的 `@ConditionalOnProperty` 先例）：

```java
@Configuration
@ConditionalOnClass(MybatisPlusInterceptor.class)
public class MybatisPlusConfig {
```

类路径有 mybatis-plus（user/team/problem）→ 正常加载；无（gateway/admin）→ 跳过，不注册、不 introspect。`@ConditionalOnClass` 用 ASM 元数据评估注解值，类缺失不会抛错，是 Spring Boot 官方设计的防护方式。

2. **admin pom.xml** — 排除 common-core 传递的 mybatis-plus（同 gateway 处理方式）：

```xml
<exclusions>
    <exclusion>
        <groupId>com.baomidou</groupId>
        <artifactId>mybatis-plus-spring-boot3-starter</artifactId>
    </exclusion>
</exclusions>
```

3. **admin application-dev.yml** — 防御性禁用 DataSource 自动配置（同 gateway）：

```yaml
spring:
  autoconfigure:
    exclude:
      - org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration
```

## 验证

`mvn spring-boot:run -pl LeetModel-admin` 越过配置阶段、Tomcat 在 8084 端口启动、`Started LeetModelAdminApplication` 日志出现即为通过。

## 面试考点

- **条件装配（Conditional）是 Spring Boot 自动配置的灵魂**：`@ConditionalOnClass` / `@ConditionalOnProperty` / `@ConditionalOnBean` 各自用途；公共 jar 里的配置类不给条件保护，等于要求所有消费方保持完全一致的依赖
- **`@ConditionalOnBean` 的代价**：评估它需要检查**所有**已注册 BeanDefinition 的类型，任何"引用缺失类的配置类"都会在此时引爆——排查时可关注条件评估阶段的 introspect 异常
- **排除依赖 ≠ 类不存在**：Maven exclusion 只是不加进 classpath，公共模块编译后的 .class 仍在 jar 里，扫描路径覆盖到就会触发类加载
