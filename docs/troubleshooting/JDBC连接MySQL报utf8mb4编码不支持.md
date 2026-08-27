# JDBC `characterEncoding=utf8mb4` 错误

> 日期：2026-07-26 | 模块：user（application-dev.yml）

---

## 报错信息

```
org.flywaydb.core.internal.exception.FlywaySqlException:
Unable to obtain connection from database:
Unsupported character encoding 'utf8mb4'
```

## 复现场景

1. Docker 启动 MySQL 8.0.33 容器
2. 启动 user 服务 → Flyway 尝试连接数据库执行迁移
3. 连接失败，整个 ApplicationContext 启动失败

JDBC URL 配置：
```yaml
url: jdbc:mysql://localhost:3306/lm_user?characterEncoding=utf8mb4&...
```

## 根因

`utf8mb4` 是 **MySQL 存储引擎内部**的字符集名称，不是 Java/ICU 标准字符集。JDBC 驱动的 `characterEncoding` 参数要求的是 **Java 标准字符集名**。

| 场景 | 应使用 |
|------|--------|
| MySQL 表 DDL（存储） | `DEFAULT CHARSET=utf8mb4` |
| JDBC 连接参数（传输） | `characterEncoding=UTF-8` |
| Java 代码 | `StandardCharsets.UTF_8` |

三者互不通用。

## 修复

```yaml
# ❌ 错误
url: jdbc:mysql://localhost:3306/lm_user?characterEncoding=utf8mb4

# ✅ 正确
url: jdbc:mysql://localhost:3306/lm_user?characterEncoding=UTF-8
```

数据存储层面仍然使用 `utf8mb4`（DDL 中已指定），JDBC 连接只需要 `UTF-8`。

## 反思

- MySQL 的 `utf8`（3 字节）和 `utf8mb4`（4 字节，支持 emoji）是 MySQL 特有的命名
- Java 中只有 `UTF-8` 一个标准名
- JDBC 连接参数写 `utf8mb4` 不会"向下兼容" MySQL 的命名——它走的是 Java 的 `Charset.forName()`
