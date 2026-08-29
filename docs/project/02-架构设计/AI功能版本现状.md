# AI 功能版本现状

> 盘点日期：2026-08-29。本文只记录已经由 POM、Controller、Service、Feign、Flyway 和前端共同证明的当前事实；后续统一契约以实际代码为准。

## 结论

三个 AI 功能都已有真实运行模块和正式业务入口。S6 已为 REVIEW 与 ASSISTANT 建立可查询版本目录和无正式业务副作用的隔离实验入口；SUGGESTION 仍未满足评价准入条件。

| featureCode | owner | 正式业务入口 | 隔离实验入口 | 当前版本来源 | 当前可评价版本 | 主要缺口 |
|---|---|---|---|---|---|---|
| `REVIEW` | ai-review-service | `/api/reviews/**`；submission-service 创建正式任务 | `POST /internal/reviews/experiments/v2`；旧入口兼容保留 | `review_version` 表与工作流注册表；V2 Flyway 初始化 `BASIC_REVIEW_V1` | `BASIC_REVIEW_V1` | 旧评审专用实验入口待外部调用方迁移并经过兼容周期后删除 |
| `ASSISTANT` | ai-assistant-service | `/api/assistant/conversations/**` | `POST /internal/assistant/conversations/experiments` | 正式会话使用 `ASSISTANT_CHAT_V1`；实验发布 `ASSISTANT_NO_RAG_V1` 与 `ASSISTANT_RAG_V1` | 两个实验版本 | 通用评价服务尚待 S7 接入；RAG 实验必须由调用方提供已构建索引版本 |
| `SUGGESTION` | ai-suggestion-service | `/api/suggestions/**` | 无 | `SuggestionV1Workflow.VERSION` 固定为 `IMPROVEMENT_V1`，任务保存 Prompt 快照 | 无 | 无版本目录与隔离实验入口；仅能基于正式提交和已完成评审创建正式建议任务 |

## 实现证据

### REVIEW

- 父工程包含 `ai-review-service`；模块具备公开与内部 Controller、Service、工作流注册表、MyBatis Mapper 和 `lm_review` Flyway。
- `ReviewFeignClient` 暴露版本列表与隔离实验；ai-evaluation-service 创建任务时只接受返回状态为 `ENABLED` 的版本。
- 隔离实验使用瞬态任务执行，不创建或覆盖正式 `review_task`；前端已具备正式评审展示和管理端评价页。
- 当前管理端仍要求手填 `workflowVersion`，这是 S6-02 要消除的缺口，不代表可以填写任意未实现版本。

### ASSISTANT

- 父工程包含 `ai-assistant-service`；模块具备会话 Controller、Service、会话/消息 Flyway、助手页面 API，以及管理端只读会话 Feign。
- 正式工作流固定记录 `ASSISTANT_CHAT_V1`、Prompt 版本和模型配置标识；启用 RAG 时还记录实际 `ragIndexVersion`。
- 内部 Feign 已增加功能版本目录与通用单轮实验。实验直接调用工作流，不创建会话和消息；无 RAG 与 RAG V1 分成两个版本，后者按物理索引名检索并禁止失败降级。

### SUGGESTION

- 父工程包含 `ai-suggestion-service`；模块具备公开与内部 Controller、任务 Service、`IMPROVEMENT_V1` 工作流、`suggestion_task` Flyway、前端建议对话框和管理端列表。
- 正式任务锁定工作流版本、来源评审版本和 Prompt 快照，通过 common-ai 调用模型，并保存结构化结果、模型名与 `aiCallId`。
- 内部 Feign 只提供任务计数和最近任务；没有版本目录或隔离实验接口，故 `IMPROVEMENT_V1` 暂不是可评价版本。

## 可评价版本准入规则

一个版本只有同时满足以下条件才可出现在评价平台候选列表：

1. owner 通过内部契约返回版本编码、状态和兼容信息，而不是由管理端硬编码或自由输入；
2. 版本状态允许创建新实验；禁用版本只保留历史快照读取能力；
3. owner 提供隔离实验入口，执行不会写入或覆盖正式评审、客服会话、消息或建议结果；
4. 运行结果能够追溯工作流版本、模型执行配置以及适用时的 RAG 索引版本。

当前准入集合为 `REVIEW / BASIC_REVIEW_V1`、`ASSISTANT / ASSISTANT_NO_RAG_V1` 和 `ASSISTANT / ASSISTANT_RAG_V1`。SUGGESTION 仍不在集合中。
