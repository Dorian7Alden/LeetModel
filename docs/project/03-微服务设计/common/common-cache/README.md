## common-cache

### 模块定位

`common-cache` 是只由后端服务引用的公共 Jar，提供 Caffeine 本地缓存、独立业务 Redis 访问、版本化 Cache Aside、Outbox 失效投递、Pub/Sub 本地失效、版本对账、故障降级、HTTP `ETag` 生成和低基数指标。

模块不识别题目、排行或其他业务字段。缓存区域、读模型、入场条件、TTL 和回源函数由数据所有者服务维护。

### 边界

- 业务 Redis 使用 Lettuce 独立连接，不注册 Spring Data `RedisConnectionFactory`，不会抢占 Token 黑名单的 Redis 自动配置。
- 缓存值由调用方显式提供 Jackson `JavaType`，不使用任意子类反序列化。
- Outbox 表位于各数据所有者的数据库，业务写入和失效事件使用同一本地事务。
- 模块禁用时返回无缓存实现，服务测试与不配置业务 Redis 的环境仍可正常回源。

### 读取与失效协议

1. 调用方用 `CacheSpec` 提供区域、作用域、结构版本、逻辑 Key 和分层 TTL，并显式提供 Jackson `JavaType`。
2. `MultiLevelCache` 先按当前代际与区域版本读取 Caffeine，再读 Redis；全部未命中时执行事实源回调并在回填前复核版本。
3. 不存在值分别使用 5 秒 L2 和 30 秒 L3 空值，单值超过 512 KiB 时直接返回但不缓存。
4. 业务写操作在本地事务中调用 `CacheInvalidator.record`。Outbox 与业务事实一起提交，提交后立即尝试投递，失败后每秒重试。
5. Lua 只接受更大的区域版本并发布消息；实例同时通过 Pub/Sub 和每五秒对账清理旧 L2。
6. Redis 故障会清空常规 L2 并切换到独立五秒降级缓存，恢复或随机代际变化后再次清空本地值。

### 默认配置

```yaml
leetmodel:
  cache:
    enabled: true
    environment: dev
    maximum-weight: 67108864
    maximum-value-bytes: 524288
    degraded-ttl: 5s
    reconcile-interval: 5000
    outbox-interval: 1000
    ttl-jitter: 0.2
    redis:
      host: localhost
      port: 6380
      database: 0
      connect-timeout: 200ms
      command-timeout: 100ms
```

业务 Redis 由 Docker Compose 的 `cache-redis` 提供，限制 256 MiB、关闭持久化并使用 `volatile-lfu`。安全状态 Redis 仍位于 `6379`。

### 验证

普通单元测试不要求外部服务；需要真实协议验证时先启动 MySQL 与业务 Redis，再显式打开门禁：

```bash
docker compose up -d --wait mysql cache-redis
RUN_CACHE_REDIS_INTEGRATION=true \
RUN_CACHE_MYSQL_INTEGRATION=true \
mvn -pl common/common-cache test
```

MySQL 集成测试只插入并清理一条 `lm_problem.cache_invalidation_outbox` 测试事件；可通过 `CACHE_MYSQL_URL`、`CACHE_MYSQL_USERNAME` 和 `CACHE_MYSQL_PASSWORD` 覆盖本地测试连接。
