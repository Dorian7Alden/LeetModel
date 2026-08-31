# common

> `common` 与 LeetModel-backend 中的公共 Maven 模块目录对应。这些模块不能独立部署，只向后端服务提供稳定的公共代码和契约。

## 模块索引

| 模块 | 职责 |
|------|------|
| [common-core/](common-core/) | 统一响应、异常、分页、实体基类和通用基础能力 |
| [common-api/](common-api/) | 跨服务 DTO、Feign 契约和降级声明 |
| [common-security/](common-security/) | 登录态、当前用户上下文、权限校验和安全异常处理 |
| [common-cache/](common-cache/) | HTTP、Caffeine、独立业务 Redis、Outbox 失效与缓存观测基础能力 |
| [common-ai/](common-ai/) | AI 网关客户端、统一调用契约和测试替身 |
| `common-messaging`，目标模块 | RocketMQ 消息信封、Outbox Relay、Inbox 幂等、配置校验、指标和测试支持；不识别具体业务事件 |

`common-ai` 已建立为 Maven 公共模块，提供统一请求与响应契约、AI 用量模型和调用 AI 网关的客户端。

`common-messaging` 是 MQ0 已确认但尚未创建的目标模块。Outbox 与 Inbox 表仍位于各数据所有者数据库，具体 Topic、Tag、Payload、任务状态与补偿规则仍由业务服务拥有。完整边界见 [RocketMQ 消息队列](../../02-架构设计/RocketMQ消息队列.md)。
