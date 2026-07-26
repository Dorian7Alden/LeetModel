# Gateway 启动报 DataSource 缺失


## 现象

Gateway 模块启动时报错：

```
Failed to configure a DataSource: 'url' attribute is not specified
and no embedded datasource could be configured.
```

即使 `application-dev.yml` 中设置了 `spring.main.web-application-type: reactive`，仍然报 DataSource 相关错误。


## 根因

Gateway 的 `pom.xml` 依赖了 `common-core` 模块。`common-core` 的传递依赖包含：

- `mybatis-plus-spring-boot3-starter` → 触发 `DataSourceAutoConfiguration`，Spring Boot 尝试自动配置数据源但找不到数据库连接信息
- `spring-boot-starter-web`（Spring MVC）→ 与 Gateway 的 WebFlux 响应式架构冲突

Gateway 本身不需要数据库也不需要 Spring MVC，这些依赖完全不应该存在。


## 修复

两步修复：

1. **Gateway pom.xml** — 从 `common-core` 依赖中排除 `mybatis-plus-spring-boot3-starter` 和 `spring-boot-starter-web`：

```xml
<dependency>
    <groupId>com.leetmodel</groupId>
    <artifactId>common-core</artifactId>
    <exclusions>
        <exclusion>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </exclusion>
        <exclusion>
            <groupId>com.baomidou</groupId>
            <artifactId>mybatis-plus-spring-boot3-starter</artifactId>
        </exclusion>
    </exclusions>
</dependency>
```

2. **application-dev.yml** — 显式禁用 DataSource 自动配置（防御性措施）：

```yaml
spring:
  autoconfigure:
    exclude:
      - org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration
```
