# user 服务启动报 Flyway 迁移校验和不一致

## 报错现象

`user-service` 启动时提示 `Migration checksum mismatch for migration version 6`，随后 MyBatis Mapper、Service 等 Bean 连锁创建失败。

## 根因分析

排行榜演示数据生成器会重写 `V6__insert_more_mock_users.sql`。脚本原先每次重新计算 BCrypt 密码哈希；即使明文密码和随机种子不变，BCrypt 的随机盐仍会让 SQL 内容变化。V6 已被 Flyway 执行后再次生成，便导致数据库记录的校验和与本地文件不一致。

后续 MyBatis 异常只是 Flyway 阻止数据源初始化后的连锁结果。

## 修复方案

演示数据生成器改为复用固定的 BCrypt 哈希，使相同输入始终生成相同迁移内容，并将 V6 恢复为数据库首次执行时的内容。不要通过关闭 Flyway 校验或随意执行 `repair` 掩盖已应用迁移被修改的问题。

验证命令：

```bash
cd LeetModel-backend
mvn -pl user-service -am test
```

启动日志应包含 `Successfully validated 6 migrations`，且测试和 Spring 上下文启动均成功。
