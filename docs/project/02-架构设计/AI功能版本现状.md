# AI 功能版本现状

> 盘点日期：2026-08-31。本文只记录已经由 POM、Controller、Service、Feign、Flyway 和前端共同证明的当前事实；后续统一契约以实际代码为准。

## 结论

三个 AI 功能都已有真实运行模块和正式业务入口。S6 已为 REVIEW 与 ASSISTANT 建立可查询版本目录和无正式业务副作用的隔离实验入口；SUGGESTION 仍未满足评价准入条件。

| featureCode | owner | 正式业务入口 | 隔离实验入口 | 当前版本来源 | 当前可评价版本 | 主要缺口 |
|---|---|---|---|---|---|---|
| `REVIEW` | ai-review-service | `/api/reviews/**`；submission-service 创建正式任务 | `POST /internal/reviews/experiments/v2`；旧入口兼容保留 | `review_version` 表与工作流注册表 | `BASIC_REVIEW_V1`、`EVIDENCE_REVIEW_V2` | V2 仍需建立专属质量真值与基线 |
| `ASSISTANT` | ai-assistant-service | `/api/assistant/conversations/**` | `POST /internal/assistant/conversations/experiments` | 正式会话使用 `ASSISTANT_CHAT_V1`；实验发布 `ASSISTANT_NO_RAG_V1` 与 `ASSISTANT_RAG_V1` | 两个实验版本 | 通用评价服务尚待 S7 接入；RAG 实验必须由调用方提供已构建索引版本 |
| `SUGGESTION` | ai-suggestion-service | `/api/suggestions/**` | 无 | `suggestion_version` 表发布 V1/V2，任务保存输入与版本快照 | 无 | 无隔离实验入口；V2 已用于正式多次手动建议，但暂不进入评价候选 |

## 实现证据

### REVIEW

- 父工程包含 `ai-review-service`；模块具备公开与内部 Controller、Service、工作流注册表、MyBatis Mapper 和 `lm_review` Flyway。
- `ReviewFeignClient` 暴露版本列表与隔离实验；ai-evaluation-service 创建任务时只接受返回状态为 `ENABLED` 的版本。
- 隔离实验使用瞬态任务执行，不创建或覆盖正式 `review_task`；前端已具备正式评审展示和管理端评价页。
- `EVIDENCE_REVIEW_V2` 复用 `REVIEW_SUBMISSION_V1` 的提交引用样本，锁定独立文本模型配置；执行时强制生成或复用 `PAPER_PARSE_V1`，结果不写入正式评审表。
- 当前管理端仍要求手填 `workflowVersion`，这是 S6-02 要消除的缺口，不代表可以填写任意未实现版本。

### ASSISTANT

- 父工程包含 `ai-assistant-service`；模块具备会话 Controller、Service、会话/消息 Flyway、助手页面 API，以及管理端只读会话 Feign。
- 正式工作流固定记录 `ASSISTANT_CHAT_V1`、Prompt 版本和模型配置标识；启用 RAG 时还记录实际 `ragIndexVersion`。
- 内部 Feign 已增加功能版本目录与通用单轮实验。实验直接调用工作流，不创建会话和消息；无 RAG 与 RAG V1 分成两个版本，后者按物理索引名检索并禁止失败降级。

### SUGGESTION

- 父工程包含 `ai-suggestion-service`；模块具备公开与内部 Controller、任务 Service、V1/V2 工作流、增量 Flyway、前端建议历史和管理端列表。
- `GROUNDED_SUGGESTION_V2` 锁定论文解析、解锁评审、实际评审依据、知识检索和生成版本；每项建议校验论文、评审、知识三段引用。
- 内部 Feign 仍只提供任务计数和最近任务；虽然已有版本表，但没有隔离实验接口，故 SUGGESTION 暂无可评价版本。

## 可评价版本准入规则

一个版本只有同时满足以下条件才可出现在评价平台候选列表：

1. owner 通过内部契约返回版本编码、状态和兼容信息，而不是由管理端硬编码或自由输入；
2. 版本状态允许创建新实验；禁用版本只保留历史快照读取能力；
3. owner 提供隔离实验入口，执行不会写入或覆盖正式评审、客服会话、消息或建议结果；
4. 运行结果能够追溯工作流版本、模型执行配置以及适用时的 RAG 索引版本。

当前准入集合为 `REVIEW / BASIC_REVIEW_V1`、`REVIEW / EVIDENCE_REVIEW_V2`、`ASSISTANT / ASSISTANT_NO_RAG_V1` 和 `ASSISTANT / ASSISTANT_RAG_V1`。SUGGESTION 仍不在集合中。
