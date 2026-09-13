# LeetModel TODO

> 本文件只保存当前任务、候选任务和满足条件后才启动的事项。已完成任务和阶段过程不在此保留；已经确认的长期边界与后续触发条件保留在本文件，具体设计和实现结论以 `docs/` 为准。

## 使用约定

- `[ ]` 表示待开始，`[~]` 表示进行中，`[!]` 表示被外部条件阻塞。
- 每次开始前阅读根 `AGENTS.md`、`README.md`、本文件、相关规范、目标模块 README 和任务指向的设计文档。
- 修改公共契约前检查全部生产者和消费者；数据库变更只新增 Flyway 迁移。
- 任务完成至少同步受影响的正式文档并运行目标模块测试；公共模块变化时验证直接消费者。
- 不在配置、日志、测试夹具或文档中保存真实密钥、Relay Token、Prompt、回答正文、知识片段或论文内容。
- new-api Relay Token 由 `ai-gateway-service` 的运行环境提供；具体脱敏和模型复核规则见 [AI 网关测试与验收](docs/project/03-微服务设计/ai-gateway-service/22-测试与验收.md)。
- 模型选型约定：将 New-API 提供的 `gemini-3.8-flash-high` 调整为平台全局首选使用的模型（涵盖客服对话、题目推荐、论文建议、评审及功能测试联调）。
- 可以自主创建本地阶段分支、执行任务卡原子 commit 和阶段 merge；未经用户明确授权，不执行 push、force push、rebase、破坏性 reset、改写历史、远端分支操作或删除用户文件。

## 当前状态

当前阶段分支：`phase/admin-console-workbench`

### [~] ADMIN-02 去除管理端无效 Hero

设计依据：[管理端工作台 UIUX 设计](docs/project/04-前端设计/07-业务模块详细设计/管理端工作台%20UIUX%20设计.md)

目标：

删除管理端各工作域中只表达口号、重复标题和装饰色彩的 `domain-hero` 区域，让核心指标、筛选与数据工作面直接进入首屏。

主流程：

1. 删除访问控制、内容中心、业务运营、AI 中枢和审计页的 Hero 标题、眉题、说明和装饰样式。
2. 访问控制与内容中心的有效管理入口迁入紧凑操作条。
3. 业务运营与 AI 中枢的刷新动作迁入紧凑工具区；审计页复用现有查询动作，不重复保留刷新。
4. 桌面与窄屏均优先展示指标、筛选或数据工作面。

完成标准：

- 管理端生产代码不再包含 `domain-hero`、Hero 渐变与装饰圆环。
- 删除的标题和说明不以另一种大卡片形式回填。
- 原 Hero 中仍有业务价值的入口与刷新动作可访问，按钮语义和加载反馈保持有效。
- 页面首屏高度明显回收，桌面与 `390px` 窄屏无新增整页横向溢出。
- 前端生产构建通过，真实浏览器验证至少覆盖一个多入口页面、一个刷新页面和审计页面。

修改范围：

- `LeetModel-frontend/src/views/admin/pages/AccessControlPage.vue`
- `LeetModel-frontend/src/views/admin/pages/ContentHubPage.vue`
- `LeetModel-frontend/src/views/admin/pages/OperationsHubPage.vue`
- `LeetModel-frontend/src/views/admin/pages/AiCenterPage.vue`
- `LeetModel-frontend/src/views/admin/pages/AuditPage.vue`
- `LeetModel-frontend/src/views/admin/style.css`
- 本反馈形成的长期设计规则

非目标：

- 不在本任务重做各工作域的数据结构、表格、抽屉或后端接口。
- 不把下一实施点“运行概览”并入本任务。

## 已确认的系统边界

- SkyWalking 是唯一 Trace/APM 实现；Prometheus 是指标与告警权威来源；运行日志不能代替操作审计。
- 业务 `traceId` 必须保留并可在 Trace 采样或遥测后端不可用时回退到结构化日志和业务事实。
- audit-service 只负责中央不可变归档与受信只读查询，不拥有用户、题目、提交、AI 任务、消息任务或生产配置等领域规则。
- 管理端通过受权限保护的服务接口执行取消、暂停、恢复、重放和回滚；Grafana、Prometheus、SkyWalking UI 只读观察。
- 遥测后端故障不得阻塞普通业务主链；审计 Outbox 保留并重试，达到严重水位时高风险治理操作 fail-closed。
- 指标、日志、Trace 和审计均遵守低基数、最小数据和脱敏边界，不保存用户/队伍/提交/任务/调用标识作为 Prometheus 标签。
- Sentinel 负责 HTTP 边缘防刷限流与 Feign 慢调用/异常熔断；严禁侵入长耗时 AI 调度和 RocketMQ 消费端。

## 执行规则

1. 每次只选择一个编号任务卡，不直接领取整个阶段。
2. 收到新的前端调整后，先将用户意图、范围和验收点记录到 `TODO.md`，再开始实现；完成前必须回看对应任务卡并逐项核对，未完成项继续保留为进行中。
3. 简单的前端样式微调不运行构建或浏览器检查；只有用户明确要求，或改动涉及逻辑、交互、契约和较大布局风险时才执行相应验证。
4. 默认由用户确认任务范围；托管模式下 Agent 按已经确认的路线图和依赖串行推进。
5. 任务依赖未满足时不得用临时硬编码绕过，应明确标记阻塞。
6. 新发现的问题若不阻断当前闭环，只记录为候选任务，不扩大当前任务。
7. 任务完成后，只把后续仍有效的决策与边界合并到所属正式文档，然后删除已完成任务卡；不保存完成历史或单独的阶段过程归档。
