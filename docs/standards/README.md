# standards

> 项目开发过程中必须遵循的规范与约束。所有模块、所有开发者（含 AI）统一执行。

---

## 文档索引

| 编号 | 文档 | 内容摘要 | 查看场景 |
|------|------|---------|---------|
| 01 | [01-document-spec.md](01-document-spec.md) | 文档撰写规范：标题等级、排版间距、图片资源与保留图片链接、README 规范 | 写任何文档前确认格式要求 |
| 02 | [02-response-spec.md](02-response-spec.md) | HTTP 响应规范：5 位业务状态码编码规则 | 定义新接口响应或新增错误码时 |
| 03 | [03-error-code-spec.md](03-error-code-spec.md) | 错误码规范：A-BB-CC 五段式编码 + 号段分配表 | 新增错误码时查阅号段和编码规则 |
| 04 | [04-development-workflow.md](04-development-workflow.md) | 开发流程规范：目标确认 → TODO 定位 → 逐条执行 | 每次开发前确认流程 |
| 05 | [05-git-commit-spec.md](05-git-commit-spec.md) | Git commit 描述规范：类型、格式、示例 | 提交代码前确认 commit message 格式 |
| 06 | [06-git-commit-workflow.md](06-git-commit-workflow.md) | Git commit 提交工作流：提交的完整操作步骤 | 实际执行 git 操作时参照 |
| 07 | [07-version-spec.md](07-version-spec.md) | 版本号规范：版本编号规则 | 发布版本或打 tag 时参照 |
| 08 | [08-database-spec.md](08-database-spec.md) | 数据库设计规范：命名、独立数据库、表结构、迁移策略 | 设计数据库表结构或新增微服务数据库时 |

---

## 使用说明

【强制】每次开始开发前，先阅读本目录中与本次任务相关的规范文档，再进入开发流程。
