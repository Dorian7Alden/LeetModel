## AI 文档与代码冲突清单

> 核验日期：2026-08-28
>
> 本文档是 D-01 的核验产物，用于锁定后续设计校准的事实基线和任务归属。本文档不代替 D-02 至 D-05 的详细设计。
>
> 处理状态：C-06 已由 S6-01 于 2026-08-29 修正；下文保留首次核验时的冲突证据，当前状态以 [AI功能版本现状.md](AI功能版本现状.md) 和对应服务 README 为准。


### 核验方法

本次将正式架构文档、微服务设计、Maven 模块、生产代码、运行配置、Flyway 迁移和自动化测试交叉比对。代码与文档不一致时，当前运行行为作为“已实现事实”，TODO 中经用户确认的总体决策作为“采用方向”。

| 编号 | 冲突主题 | 当前实现事实 | 采用方向 | 后续任务 |
|------|----------|--------------|----------|----------|
| C-01 | RAG 服务归属 | 尚无运行时 RAG，旧文档将检索归给 `ai-review-service` | RAG V1 归 `ai-assistant-service` | D-03 |
| C-02 | RAG V1 与 V2 定义 | 旧文档将标签加 AI 选文称为 V1，将向量检索称为 V2 | 向量 RAG 为 V1，AI 目录导航为 V2 | D-03 |
| C-03 | new-api 接入状态 | new-api 只作为 Docker 基础设施运行，AI 网关仍直连供应商 | 保持“已部署、未接入”的当前状态，后续切换为两层网关链路 | D-02，S1 |
| C-04 | AI 网关本地并发保护 | 设计声称首版已有信号量和有界等待，生产调用链中未实现 | 不再把本地并发保护当作已有能力，调度和容量设计以无保护基线开始 | D-02，S5 |
| C-05 | AI 评价综合分 | 代码和数据库仍计算并保存 `overallScore`，正式稳定性设计禁止综合质量分 | 稳定性评价保留原始统计量；加权结果只能是特定目标下的“版本选择指数” | D-04，S7，S8 |
| C-06 | suggestion 模块状态 | 父 POM、启动类、接口、工作流、Flyway 和测试均已存在 | 将 `ai-suggestion-service` 视为真实运行模块，不得再标记为“尚无 Maven 模块” | D-01 立即确认，S6-01 完整盘点 |


### RAG 归属冲突

#### 首次核验时的文档位置

- `docs/project/02-架构设计/RAG知识库.md` 的“API 契约”将 RAG 归给 `ai-review-service`。
- 同文档首部声称知识库已归档到 `legacy/knowledge-base/`。
- `TODO.md` 的已确认总体决策和阶段 4 将第一版 RAG 归给 `ai-assistant-service`，知识源为 `rag_kb/`。

#### 代码证据

- `LeetModel-backend/ai-assistant-service/pom.xml` 尚无 LangChain4j RAG 和 Elasticsearch 依赖。
- `LeetModel-backend/ai-assistant-service` 中尚无 `ContentRetriever`、`EmbeddingStore` 或项目自定义检索器实现。
- `rag_kb/` 已是实际存在的独立知识内容目录，且其 `.kb/` 中保留文件夹标签与 AI 导航规范。

#### 采用结论

RAG V1 归 `ai-assistant-service`。`ai-review-service` 不是第一版知识检索的所有者，`legacy/knowledge-base/` 不是当前知识源。D-03 必须同步校准 RAG 架构文档、assistant README、根 `AGENTS.md` 与 `rag_kb` 行为说明。


### RAG 版本定义冲突

#### 文档位置

- `docs/project/02-架构设计/RAG知识库.md` 将标签树扫描、AI 选标签和 AI 判断笔记的方案称为 V1。
- 同文档将 Elasticsearch 向量或混合检索称为 V2。
- `TODO.md` 已确认相反的版本方向。

#### 代码证据

项目尚未实现任何 RAG 运行链路，因此不存在需要兼容的已发布 RAG V1 代码契约。当前 LangChain4j `0.34.0` 仅被评审服务用于 Prompt 模板，不能证明向量 RAG 已实现。

#### 采用结论

使用常规向量检索的 LangChain4j 加 Elasticsearch 方案命名为 RAG V1；由 AI 读取受控目录并选择文档的方案命名为 RAG V2。D-03 负责重写完整架构定义，S9 只负责 RAG V2 设计。


### new-api 接入状态冲突

#### 文档位置

- D-01 核验时的 `docs/project/02-架构设计/new-api第三方网关集成.md` 版本说明 new-api 只完成本地 Docker 部署，尚未接入 LeetModel AI 调用链；该状态随后已由 S1 改变。
- 根 `README.md` 在 D-01 核验时标明 `ai-gateway-service` 尚未切换调用链。

#### 代码与配置证据

- `LeetModel-backend/docker-compose.yml` 定义了 `calciumion/new-api:v1.0.0-rc.26` 和 `127.0.0.1:3000`。
- D-01 核验时，`application.yml` 仍配置供应商官方地址并直接读取供应商密钥。
- D-01 核验时，`DeepSeekAdapter` 和 `KimiAdapter` 仍是生产适配器；S1 已增加 new-api 配置与模型映射并删除这些旧实现。

#### 采用结论

D-01 的采用结论是“已部署但未接入”；S1 随后完成真实迁移并以客服文本、论文评审多模态冒烟验收。当前状态以 S1 结果和正式集成文档为准。


### AI 网关并发保护冲突

#### 文档位置

- `docs/project/03-微服务设计/ai-gateway-service/README.md` 多处将单实例并发限制和有界等待列为首版主流程或已有责任。
- `01-统一调用与路由.md`、`08-限流与容量治理.md` 和 `22-测试与验收.md` 也将信号量、等待超时和并发验收写入 0-1 范围。

#### 代码证据

`AiChatService.chat` 的生产主链是路由校验、能力校验、获取适配器、同步调用和审计记录。该服务及其生产配置中没有信号量、并发许可、有界等待、本地限流器或对应超时参数。

#### 采用结论

本地并发保护是“已设计但未实现”，不得作为后续调度的现有前置。D-02 校准 new-api 与 LeetModel 的限流、重试和容量边界；S5 从无队列、无本地容量保护的实测基线开始。


### AI 评价综合分冲突

#### 文档位置

- `docs/project/03-微服务设计/ai-evaluation-service/稳定性评价/统计规则.md` 明确禁止将稳定性、成功率和耗时合成综合质量分。
- 同目录的“评价指标”和“输出边界”说明无标准答案或人工标注时，不评价语义正确性。

#### 代码与数据库证据

- `EvaluationMetricsCalculator` 仍将有效性、极差归一化稳定性与延迟按权重合成 `overallScore`。
- `EvaluationTask`、`EvaluationTaskMapper` 和 `EvaluationService` 仍读写综合分。
- Flyway `V1__create_evaluation_tables.sql` 仍包含 `stability_score`、`latency_score` 和 `overall_score`。
- `EvaluationMetricsCalculatorTest` 仍断言 `overallScore` 的固定数值。

#### 采用结论

当前代码实现的 `overallScore` 是待迁移旧口径，不是稳定性评价的正式目标。D-04 必须分开稳定性、质量、资源、归一化和加权指数；S7 保留可解释的原始指标；S8 只能把特定口径的加权结果命名为“版本选择指数”，不得称为准确率或客观质量分。


### suggestion 模块状态冲突

#### 文档位置

- `docs/project/03-微服务设计/ai-suggestion-service/README.md` 曾声称当前服务尚无 Maven 运行模块，整张流程图都是目标设计；S6-01 已修正。
- `docs/project/02-架构设计/AI系统分层.md` 和根 `README.md` 已将该服务记录为实际运行模块。

#### 代码与数据库证据

- `LeetModel-backend/pom.xml` 已声明 `<module>ai-suggestion-service</module>`。
- 模块已包含 `SuggestionApplication`、用户与内部 Controller、`SuggestionService`、`SuggestionV1Workflow` 和 PDF 文本提取能力。
- 模块已包含 Flyway `V1__create_suggestion_task.sql`、运行配置、Prompt 模板和三组自动化测试。

#### 采用结论

`ai-suggestion-service` 是真实 Maven 运行模块。D-01 固定该事实；S6-01 已完成正式入口、数据所有权、版本来源和未完成缺口盘点，并明确它尚未具备隔离实验入口。


### 后续正式文档更新表

| 后续任务 | 必须更新的正式文档 | 必须消除的冲突 |
|----------|--------------------------|--------------------|
| D-02 | `AI系统分层.md`、`new-api第三方网关集成.md`、`ai-gateway-service/README.md` 及 01 至 19 | C-03、C-04 |
| D-03 | `RAG知识库.md`、`ai-assistant-service/README.md`、根 `AGENTS.md`、`rag_kb` 行为说明 | C-01、C-02 |
| D-04 | `ai-evaluation-service/README.md` 及稳定性评价全部原子文档、`admin-service/AI测试控制`、`admin-service/AI调用监控` | C-05 |
| D-05 | 上述 AI 架构和服务文档中使用版本标识的位置 | 防止在新设计中继续混用五类版本标识 |
| S6-01 | `ai-suggestion-service/README.md` 及相关架构索引 | C-06 |


### D-01 边界

D-01 只完成冲突发现、事实核验、采用方向和后续责任的固化。new-api 责任重划、RAG 正式架构、评价术语与五类版本标识仍必须由 D-02 至 D-05 分别完成。
