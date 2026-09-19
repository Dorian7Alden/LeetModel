# Token 黑名单查询被 Lettuce 默认超时拖住

> 影响范围：gateway-service、common-security、依赖安全状态 Redis 的登录态校验

## 报错现象

Token 黑名单按 “Redis 不可用时 fail-open 放行” 设计实现后，功能验证阶段发现：命中黑名单与正常鉴权都符合预期，但把安全状态 Redis `6379` 停掉后，携带有效 Token 访问受保护接口不再返回 200，而是 curl 在 15 秒后以 `000`（连接超时）结束，网关日志里只有 Lettuce 的告警：

```text
Cannot reconnect to [localhost/<unresolved>:6379]: finishConnect(..) failed: Connection refused
```

同一时刻直接访问业务服务 `8083` 却能在约 0.9 秒内返回 200，并累加降级计数。

## 根因分析

两个独立原因叠加：

1. Lettuce 默认命令超时是 60 秒。Redis 不可用时客户端会先尝试重连，命令排队等待而不是立刻抛错，因此 `onErrorResume` 降级分支迟迟不触发，请求被拖到默认超时。
2. 服务侧（阻塞式 `StringRedisTemplate`）与网关（`ReactiveStringRedisTemplate`）行为不一致：服务侧在补上 Lettuce 客户端超时定制后能在 1 秒内降级；网关这次并发保护依赖响应式过滤器的错误信号，Lettuce 未报错时它不会触发，因此网关仍然挂住。

## 修复方案

1. 配置层：`TokenBlacklistProperties.redisTimeoutMs`（默认 300 毫秒），由 `TokenBlacklistRedisConfiguration` 通过 `LettuceClientConfigurationBuilderCustomizer` 同时设置命令超时与连接超时。
2. 网关层：在 `TokenBlacklistGlobalFilter` 的响应式链路上显式加 `Mono.timeout(...)`，即使 Lettuce 处于重连状态也能在超时后进入降级分支，不阻塞事件循环。
3. 测试：补充 “Redis 长时间无响应（`Mono.never()`）时按超时降级” 的用例，防止回归。

## 回归验证

- 正常状态：携带有效 Token 访问受保护接口返回 200，耗时约 0.5 秒。
- 停掉 `6379`：网关在 0.7 秒内返回 200（fail-open），`auth_token_blacklist_degraded_total` 累加；业务服务侧同样降级放行。
- 恢复 `6379`：已登出 Token 仍被拒绝（401），新登录 Token 正常访问。

## 经验

- “降级”必须连带配置超时。缓存或注册中心类依赖在故障时先重连，默认超时往往是几十秒，降级代码写得再对也不会被执行到。
- 阻塞式客户端与响应式客户端对同一故障的表现可能不同：阻塞调用会等到超时抛异常，响应式链路可能一直没有错误信号，必须显式加超时操作符。
- 验收降级能力时要真的把依赖停掉测一次，只看代码分支覆盖不算验证。
