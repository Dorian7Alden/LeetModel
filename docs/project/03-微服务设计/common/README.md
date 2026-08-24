# common

> `common` 与 LeetModel-backend 中的公共 Maven 模块目录对应。这些模块不能独立部署，只向后端服务提供稳定的公共代码和契约。

## 模块索引

| 模块 | 职责 |
|------|------|
| [common-core/](common-core/) | 统一响应、异常、分页、实体基类和通用基础能力 |
| [common-api/](common-api/) | 跨服务 DTO、Feign 契约和降级声明 |
| [common-security/](common-security/) | 登录态、当前用户上下文、权限校验和安全异常处理 |
| [common-ai/](common-ai/) | AI 网关客户端、统一调用契约和测试替身 |

`common-ai` 是已确定但尚未创建代码目录的目标 Maven 模块。它的英文目录名与架构设计保持一致。
