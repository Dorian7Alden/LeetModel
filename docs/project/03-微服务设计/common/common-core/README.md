# common-core

> `common-core` 提供不包含具体业务语义的通用基础能力。

## 职责边界

- 负责统一响应、基础异常、分页、实体基类和稳定的基础设施客户端。
- 负责不含业务语义的关联快照、MDC 作用域、线程池传播和遥测字段约束。
- 负责 `leetmodel.log.v1` 最终输出的字段白名单、不可逆脱敏、注入防护、限长，以及按稳定编码聚合重复故障的公共限频器。
- 负责向 SkyWalking OAP `/v3/logs` 异步上报安全 JSON 的单线程有界 Reporter，以及成功、失败、丢弃、队列、连接和恢复的低基数指标。
- 不接受请求/响应正文、论文、Prompt、回答、知识片段、Embedding、消息 Payload、对象路径或凭据作为公共日志字段。
- 不定义用户、团队、题目、提交或评审业务规则。
- 不为未来可能复用提前放入业务工具类。

## 文档索引

| 文档 | 内容摘要 |
|------|----------|
| [统一响应与数据契约.md](统一响应与数据契约.md) | Result、PageResult、BaseEntity、BasePageQuery、MyBatis-Plus 分页与 Jackson 序列化 |
| [异常体系与全局拦截.md](异常体系与全局拦截.md) | 错误码分段规范、BusinessException 业务异常与 GlobalExceptionHandler 统一拦截 |
| [请求拦截与全链路追踪.md](请求拦截与全链路追踪.md) | TraceIdServletFilter 进站过滤、CorrelationContext 上下文快照与线程池 MDC 透传 |
| [结构化日志与指标上报.md](结构化日志与指标上报.md) | leetmodel.log.v1 白名单 JSON 日志、不可逆脱敏、故障限频与 SkyWalking OAP 异步上报 |
| [对象存储服务.md](对象存储服务.md) | StorageService 统一存储抽象、MinIO 预签名链接、路径规范与条件自动装配 |
