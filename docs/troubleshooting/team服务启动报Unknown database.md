# Team 服务启动报 Unknown database 'leetmodel_team'

---

## 报错现象

Team 服务（端口 8082）启动失败，错误链从 `internalTeamController → teamServiceImpl → teamMemberMapper → flywayInitializer` 一路传导：

```text
Error creating bean with name 'flywayInitializer' ...:
Unable to obtain connection from database: Unknown database 'leetmodel_team'
SQL State  : 42000
Error Code : 1049
```

---

## 根因分析

三层原因叠加：

1. **数据库名违规**：datasource URL 写作 `jdbc:mysql://localhost:3306/leetmodel_team`，而 `docs/standards/08-database-spec.md`【强制】规定数据库名统一 `lm_{service}` 全小写下划线，团队服务应为 `lm_team`。`leetmodel_team` 是 team 模块创建时沿用了 `leetmodel_` 旧拼写，未跟上 `lm_` 改名决策（user/problem 均已用 `lm_user` / `lm_problem`）。
2. **缺自动建库参数**：URL 缺少 `createDatabaseIfNotExist=true`（user/problem 都有该参数），而 docker-compose 又不挂 `/docker-entrypoint-initdb.d` 初始化脚本——库从未被创建。
3. **Flyway 启动早期连接目标库执行迁移** → 连不存在的库 → MySQL 返回 42000/1049。

---

## 修复方案

修改 `LeetModel-team/src/main/resources/application-dev.yml` 的 datasource 配置，与 user/problem 完全对齐：

```yaml
spring:
  flyway:
    enabled: true
    locations: classpath:db/migration
  datasource:
    url: jdbc:mysql://localhost:3306/lm_team?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&createDatabaseIfNotExist=true
```

- 库名 `leetmodel_team` → `lm_team`（遵守命名规范）
- 追加 `&createDatabaseIfNotExist=true`（启动时自动建库）
- `characterEncoding=utf8` → `characterEncoding=UTF-8`（对齐其它服务；JDBC 只认 Java 标准字符集名，见 [JDBC连接MySQL报utf8mb4编码不支持.md](JDBC连接MySQL报utf8mb4编码不支持.md)）

验证：重启后 Flyway 日志显示 `Successfully applied 1 migration to schema lm_team`，`lm_team` 库中生成 `flyway_schema_history`、`team`、`team_member` 表。

---

## 延伸：修复后暴露的第二个问题（缺少 Feign 扫描）

数据库修复后启动继续失败：

```text
Parameter 0 of constructor in com.leetmodel.common.security.handler.StpInterfaceImpl
required a bean of type 'com.leetmodel.common.api.feign.UserFeignClient' that could not be found.
```

**根因**：team 服务的 pom 显式引入了 `common-security`（其 `StpInterfaceImpl` 构造器注入 `UserFeignClient`）和 `common-api` + `loadbalancer`，但启动类缺少 `@EnableFeignClients` 扫描注解，Feign 代理 Bean 未创建。

**修复**：在 `LeetModelTeamApplication` 上补充（与 user 服务一致）：

```java
@EnableFeignClients(basePackages = "com.leetmodel.common.api.feign")
```

**与 Problem 服务同类问题的对比**（面试考点）：Problem 服务曾遇到**完全相同的报错**（[Problem服务启动报UserFeignClient Bean缺失.md](Problem服务启动报UserFeignClient Bean缺失.md)），但修复方向相反——从 POM 移除 `common-security`。两条路线的判断依据是**服务是否需要服务内鉴权**：

| 服务 | 是否需服务内鉴权 | 处理方式 |
|------|----------------|---------|
| user | 是（RBAC 权限管理） | 保留 common-security + `@EnableFeignClients` |
| team | 是（`UserContext.getUserId()` 取当前用户，D-11 成员权限控制） | 同 user：补 `@EnableFeignClients` |
| problem | 否（鉴权统一在 Gateway 层） | 移除 common-security 依赖 |

判断方法：看服务代码是否使用 `common.security` 包下的类（如 `UserContext`、`@SaCheckPermission`）。用到了 → 补 Feign 扫描；没用到 → 按需剔除依赖。

---

## 知识点

**MySQL `createDatabaseIfNotExist` 自动建库**：JDBC URL 参数，驱动连接时检测库不存在则创建（需连接用户有建库权限）。配合 Flyway 可实现"配置即建库、启动即迁移"，省去手工建库步骤——但要注意库名写错时该参数不会帮你"改名"，只会静默创建一个错误命名的库，因此库名仍须遵守命名规范。

**微服务公共模块引入的三种形态**：`common-core`（基础工具）、`common-security`（Sa-Token 鉴权，依赖 Feign 查角色权限）、`common-api`（Feign 接口声明）。新服务创建时应先判断是否需要服务内鉴权，再决定依赖组合与启动类注解，避免启动时 Bean 缺失。
