# standards

> 项目开发过程中必须遵循的规范与约束。所有模块、所有开发者（含 AI）统一执行。

---

## 文档索引

| 编号 | 文档 | 内容摘要 | 查看场景 |
|------|------|---------|---------|
| 01 | [01-document-spec.md](01-document-spec.md) | 文档撰写规范：先总体后分述、标题排版、图片资源和 README 规范 | 写任何文档前确认格式与内容组织要求 |
| 02 | [02-response-spec.md](02-response-spec.md) | HTTP 响应规范：5 位业务状态码编码规则 | 定义新接口响应或新增错误码时 |
| 03 | [03-error-code-spec.md](03-error-code-spec.md) | 错误码规范：A-BB-CC 五段式编码 + 号段分配表 | 新增错误码时查阅号段和编码规则 |
| 04 | [04-development-workflow.md](04-development-workflow.md) | 单目标开发规范：任务卡、独立分支、端到端验收与完成定义 | 每次开发前确认流程 |
| 05 | [05-git-workflow-spec.md](05-git-workflow-spec.md) | Git 分支与提交规范：单目标分支、原子提交、合并消息和完整操作流程 | 创建分支、提交或合并前确认 |
| 07 | [07-version-spec.md](07-version-spec.md) | 版本号规范：版本编号规则 | 发布版本或打 tag 时参照 |
| 08 | [08-database-spec.md](08-database-spec.md) | 数据库设计规范：命名、独立数据库、表结构、迁移策略 | 设计数据库表结构或新增微服务数据库时 |
| 09 | [09-knife4j-annotation-spec.md](09-knife4j-annotation-spec.md) | Knife4j 接口注解规范：必须写 @Operation 且置于请求映射注解之前 | 编写 Controller 接口时 |
| 10 | [10-exception-handling-spec.md](10-exception-handling-spec.md) | 统一项目异常处理规范：BusinessException、throwIf、全局异常处理器、Feign 降级 | 编写 Service 或 Controller 异常逻辑时 |
| 11 | [11-code-style-spec.md](11-code-style-spec.md) | 后端 Java 代码风格规范：小方法、步骤注释、早返回、Stream 与集合组装 | 编写或调整 Java 代码时 |
| 12 | [12-microservice-design-document-spec.md](12-microservice-design-document-spec.md) | 微服务设计文档规范：服务 README 整体图、服务归属、功能目录和跨服务边界 | 新增、拆分或更新微服务设计时 |

---

## 使用说明

【强制】每次开始开发前，先阅读本目录中与本次任务相关的规范文档，再进入开发流程。
