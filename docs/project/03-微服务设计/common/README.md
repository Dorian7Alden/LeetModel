# common

> `common` 与 LeetModel-backend 中的公共 Maven 模块目录对应。这些模块不能独立部署，只向后端服务提供稳定的公共代码和契约。

## 模块索引

| 模块 | 职责 |
|------|------|
| [common-core/](common-core/) | 统一响应、异常、分页、实体基类和通用基础能力 |
| [common-api/](common-api/) | 跨服务 DTO、Feign 契约和降级声明 |
| [common-security/](common-security/) | 登录态、当前用户上下文、权限校验和安全异常处理 |
| [common-cache/](common-cache/) | HTTP、Caffeine、独立业务 Redis、Outbox 失效与缓存观测基础能力 |
| [common-messaging/](common-messaging/) | RocketMQ 消息信封、事务 Outbox、Inbox 幂等、租约 Relay、指标、脱敏运维端点和 consumer 控制 |
| [common-ai/](common-ai/) | AI 网关客户端、统一调用契约和测试替身 |

`common-ai` 已建立为 Maven 公共模块，提供统一请求与响应契约、AI 用量模型和调用 AI 网关的客户端。

`common-messaging` 已建立公共契约、可靠传递和 MQ6 本地运维能力。Outbox 与 Inbox 表仍位于各数据所有者数据库；公共端点只返回脱敏元数据，并将真实 consumer 暂停/恢复和原事件受控补发交给数据所有者执行。具体 Topic、Tag、Payload、任务状态与补偿规则仍由业务服务拥有。完整边界见 [RocketMQ 消息队列](../../02-架构设计/RocketMQ消息队列.md)。
