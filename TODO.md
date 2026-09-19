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

最近完成的阶段是「平台能力增强阶段」，已按用户验收合入 `dev` 并作为 `v2.2.0` 发布：统一文件资产身份、预签名分片直传与超大附件、题库全文检索、评测评分口径文档均已完成并验收。发布后 `dev` 与 `master` 位于同一发布基线。

最近完成的阶段是「Token 主动失效」：B1 已实现、真实验收并作为 `v2.3.0` 发布（阶段分支 `phase/token-blacklist` 已合入 `dev` 并删除），B2（管理员按用户强制下线）按用户决定不做。当前没有进行中的阶段或任务卡；下一轮开发由用户提出目标后建立任务卡并从 `dev` 创建新阶段分支。长期有效的设计入口：文件资产见 [文件资产管理架构](docs/project/02-架构设计/文件资产管理架构.md)，题库检索见 [公开题库全文检索](docs/project/03-微服务设计/problem-service/公开题库/全文检索.md)，评测口径见 [指标来源与组合影响](docs/project/03-微服务设计/ai-evaluation-service/权重与选择指数/指标来源与组合影响.md)，Token 黑名单见 [认证鉴权](docs/project/03-微服务设计/common/common-security/认证鉴权.md)。

## 当前阶段：Token 主动失效（候选分支 phase/token-blacklist）

### 阶段目标

落地文档、代码注释与简历均已声明、但实现缺失的「JWT 无状态签发 + Redis 黑名单主动失效」：让登出后的 Token 立刻失效，并让管理员强制下线具备可实现口径。

### 现状与关键补充（防止理解偏差）

1. Sa-Token 1.38 的 JWT 无状态模式**没有内置黑名单**。`sa-token-core` 中不存在 blacklist 实现；`StpLogicJwtForStateless.logout()` 只删除本地 cookie 与 storage，不写任何服务端存储；`StpUtil.kickout()` 在没有服务端会话时同样不产生效果。因此必须自建机制，加 `sa-token-redis-*` 依赖解决不了这个问题（那套是给有状态会话用的）。
2. 修复前实测现象：登录 → 带 Token 访问 `/api/problems` 返回 200 → 调用 `POST /api/auth/logout` → 同一 Token 仍返回 200；安全状态 Redis 6379 键数为 0。该问题已由 B1 修复，验收结果见下。
3. 声明该能力的位置：`TokenUtil` 类注释、[认证鉴权.md](docs/project/03-微服务设计/common/common-security/认证鉴权.md) 4.1、[技术栈选型.md](docs/project/02-架构设计/技术栈选型.md)（JWT + Redis 黑名单）、[缓存策略.md](docs/project/02-架构设计/缓存策略.md)（安全隔离表）、网关 `application-dev.yml` 的 Redis 注释。属于“文档先写、实现未落地”，本阶段补齐实现并让文档与实现一致。
4. 当前 JWT 载荷只有 `loginType`、`loginId`、`device`、`eff`、`rnStr`，没有 `iat` 与 `jti`。因此：Token 粒度失效用 Token 的 SHA-256 指纹做键；用户粒度强制下线需要额外口径（在登录时写入签发时间 extra claim，或引入 Token 版本号）。
5. 基础设施已具备：`common-security` 已引入 `spring-boot-starter-data-redis`，gateway 已引入 reactive 版本；user、gateway、team、admin 的 dev 配置已指向 6379；compose 的 6379 未显式声明 `maxmemory-policy`（Redis 默认即 `noeviction`，文档要求显式声明）。
6. 网关是唯一对外入口，但业务服务端口（8081 等）本身可达，只做网关校验会被“绕过网关直连服务”绕开，校验点必须覆盖网关与服务侧。

### 任务卡清单

| 顺序 | 任务卡 | 目标摘要 | 依赖 | 状态 |
|------|--------|----------|------|------|
| 1 | B1 | 登出 Token 黑名单与双端校验 | 无 | 已完成，待用户验收 |
| 2 | B2 | 管理员按用户强制下线 | B1 | 已确认不做 |

#### B1 登出 Token 黑名单与双端校验

- 背景：简历与项目文档都声称“网关校验 JWT 签名并查询 Redis 黑名单拦截失效 Token”，实测登出后旧 Token 仍可用，属于安全能力缺口。
- 目标：登出即失效；黑名单存于安全状态 Redis 6379，按 Token 剩余有效期设置 TTL；网关与业务服务两处校验；Redis 不可用时按配置降级并产生可观测信号。
- 入口：`POST /api/auth/logout`（写黑名单）、网关 `SaReactorFilter` 与服务侧 `SaInterceptor`（读黑名单）。
- 主流程：登录签发 JWT（不变）→ 登出时计算 Token 指纹并写入黑名单（TTL = 该 Token 剩余有效期）→ 后续请求先校验签名、再查指纹，命中即返回 401（错误码 40101）→ Redis 异常时按 `auth.token-blacklist.fail-closed` 决定放行或拒绝，并累加降级计数。
- 实现过程：
  1. 设计文档：在 [认证鉴权.md](docs/project/03-微服务设计/common/common-security/认证鉴权.md) 增加“黑名单实现口径”章节（键结构、TTL、指纹、降级、观测、多实例、与无状态签发的关系），并同步技术栈选型、缓存策略、concepts 中“计划态”表述。
  2. `common-security`：新增 `TokenFingerprint`（SHA-256，不落明文）、`TokenBlacklistProperties`、`TokenBlacklistService`（`revoke`/`isRevoked`，含降级与 Micrometer 计数器）；改造 `TokenUtil.logout()`：先取 Token 与剩余有效期 → 写黑名单 → 再执行本地登出。
  3. `common-security` 服务侧校验：`SaTokenAnnotationConfig` 的 `SaInterceptor` 在注解校验前先查黑名单，命中抛统一未登录异常。
  4. `gateway-service`：使用 `ReactiveStringRedisTemplate` 实现 `TokenBlacklistReactiveFilter`，在登录校验通过后查指纹，命中返回既有 401 统一响应。
  5. 配置：`auth.token-blacklist.enabled`、`key-prefix`、`fail-closed`、`max-ttl-seconds`；compose 为 6379 显式声明 `--maxmemory-policy noeviction`；核对各服务 Redis 指向 6379。
  6. 测试：TTL 计算、指纹不含明文、命中/未命中、Redis 异常的两条降级分支、网关与服务侧过滤链单测。
  7. 真实验收：登录 → 200 → 登出 → 401；重启网关后旧 Token 仍被拒（证明不是进程内状态）；Redis 键名与 TTL 符合预期；停掉 Redis 时行为与配置一致；受影响模块测试全绿。
- 修改范围：`common-security`、`gateway-service`、`user-service`（登出链路）、`compose.yaml`、`docs/`、相关测试。
- 完成标准：登出后旧 Token 在网关与直连服务两条路径都被拒绝；黑名单键使用指纹且带 TTL；降级行为可配置且有指标；文档与实现一致；真实链路验收通过。
- 非目标：不引入服务端会话（保持无状态签发）、不做单点登录互踢、不改前端登出逻辑、不改 RBAC 与注解鉴权语义。

#### B2 管理员按用户强制下线（可选，待确认）

- 背景：`TokenUtil.kickout(Long userId)` 与文档都声称支持“踢人下线”，但无状态 JWT 无法枚举某用户已签发的 Token。
- 目标：提供按用户维度批量失效口径，使“强制下线”可落地。
- 主流程：登录时在 JWT 写入签发时间（`SaLoginModel` extra claim）→ 强制下线时写 `auth:user-revoke:{userId}` 吊销水位（TTL 取 Token 最长有效期）→ 校验时比较 Token 签发时间与水位，早于水位即拒绝。
- 完成标准：强制下线后该用户此前签发的所有 Token 立即失效，之后重新登录的 Token 正常可用；有单测与真实验收。
- 非目标：不做在线会话列表与单设备踢出。

### B1 实际验收结果（2026-09-19）

- 登录 → 登出前经网关访问受保护接口 200 → `POST /api/auth/logout` → 同一 Token 经网关返回 401（`40101`，提示“登录已失效，请重新登录”）。
- 同一 Token 直连 problem-service `8083` 同样返回 401，证明服务侧防线有效。
- 安全状态 Redis 出现 `leetmodel:auth:blacklist:{sha256}`，TTL 为 Token 剩余有效期（实测 604798 秒）。
- 重启网关后旧 Token 仍被拒绝，说明黑名单来自 Redis 而非进程内状态；重新登录签发的新 Token 正常访问。
- 停掉 6379：网关 0.7 秒内 fail-open 放行（默认 `fail-closed=false`）并累加 `auth_token_blacklist_degraded_total`，业务服务侧同样降级；恢复后旧 Token 仍被拒、新 Token 正常。
- 受影响的五个模块测试全绿：common-core 55、common-security 7、user-service 38、problem-service 72、gateway-service 17，零失败。

### 已确认的实现口径

1. 只做 B1，不实现管理员按用户强制下线（无状态 JWT 无法枚举用户已签发 Token）。
2. Redis 不可用默认 `fail-open`（`auth.token-blacklist.fail-closed=false`），可配置切换为 fail-closed。
3. 网关与业务服务两侧都校验，避免绕过网关直连服务端口。
4. 阶段分支名为 `phase/token-blacklist`，完成后 `--no-ff` 合入 `dev`。

## 已确认的系统边界

- SkyWalking 是唯一 Trace/APM 实现；Prometheus 是指标与告警权威来源；运行日志不能代替操作审计。
- 业务 `traceId` 必须保留并可在 Trace 采样或遥测后端不可用时回退到结构化日志和业务事实。
- audit-service 只负责中央不可变归档与受信只读查询，不拥有用户、题目、提交、AI 任务、消息任务或生产配置等领域规则。
- 管理端通过受权限保护的服务接口执行取消、暂停、恢复、重放和回滚；Grafana、Prometheus、SkyWalking UI 只读观察。
- 遥测后端故障不得阻塞普通业务主链；审计 Outbox 保留并重试，达到严重水位时高风险治理操作 fail-closed。
- 指标、日志、Trace 和审计均遵守低基数、最小数据和脱敏边界，不保存用户/队伍/提交/任务/调用标识作为 Prometheus 标签。
- Sentinel 负责 HTTP 边缘防刷限流与 Feign 慢调用/异常熔断；严禁侵入长耗时 AI 调度和 RocketMQ 消费端。
- 正式文件统一以稳定 fileId 引用，业务服务不保存对象路由；论文临时分片仍由 submission-service 管理，合并后的正式论文由 file-service 接管。

## 执行规则

1. 每次只选择一个编号任务卡，不直接领取整个阶段。
2. 收到新的前端调整后，先将用户意图、范围和验收点记录到 `TODO.md`，再开始实现；完成前必须回看对应任务卡并逐项核对，未完成项继续保留为进行中。
3. 简单的前端样式微调不运行构建或浏览器检查；只有用户明确要求，或改动涉及逻辑、交互、契约和较大布局风险时才执行相应验证。
4. 默认由用户确认任务范围；托管模式下 Agent 按已经确认的路线图和依赖串行推进。
5. 任务依赖未满足时不得用临时硬编码绕过，应明确标记阻塞。
6. 新发现的问题若不阻断当前闭环，只记录为候选任务，不扩大当前任务。
7. 任务完成后，只把后续仍有效的决策与边界合并到所属正式文档，然后删除已完成任务卡；不保存完成历史或单独的阶段过程归档。

## 暂缓与不做

- 审计完整实体快照：不做，保留 `beforeSummary`/`afterSummary` 设计。
- 强制物理删除文件：仍按既有设计暂缓，需要独立高风险权限与审计后再评估。
- Markdown 外链自动本地化、静态资源动态替换、压缩包在线解压：非目标。

## 候选任务

- **更新实体不刷新 `update_time`**。现象：通过管理端接口修改题目标题后，`lm_problem.problem.update_time` 仍是创建时刻（实测创建 `15:13:32`，6 秒后改名成功，库中 `create_time` 与 `update_time` 均为 `15:13:32`）。根因：`common-core` 的 `MybatisPlusConfig.metaObjectHandler()` 使用 `strictUpdateFill` 填充 `updateTime`，MyBatis-Plus 在字段已有值时跳过填充，而各服务普遍采用“先查实体、再 `updateById`”的写路径，实体始终带着旧时间戳。影响范围：管理端“最后更新时间”、审计快照时间和题目检索文档的 `updateTime` 均不反映真实修改时刻；不影响题目检索排序（排序用年份与题号）与主流程，因此未在上一阶段处理。处理建议：填充改为无条件覆盖或在更新路径显式置空 `updateTime`，切换前需对全部写入路径做回归，避免覆盖业务显式设置的时间。
