# new-api 第三方网关集成

## 问题发现

LeetModel 当前的 `ai-gateway-service` 已自行实现 DeepSeek、Kimi 等供应商协议适配、模型目录、场景路由、能力校验和最小调用审计。继续扩展多供应商协议、渠道权重、故障重试、额度和成本治理，会与成熟开源 AI 网关 new-api 的通用能力重复。

重复建设会增加供应商适配、渠道治理和测试成本，也会让 LeetModel 的业务 AI 设计与通用网关基础设施耦合。独立开发者同时维护业务工作流和完整 AI 网关，超出当前项目重点。

## 阶段性决策

new-api 作为独立第三方基础设施服务部署，不复制源码、不合并 Maven 模块，也不与 LeetModel 共享代码生命周期。长期目标调用关系为：

```text
AI 业务服务
    -> LeetModel ai-gateway-service
    -> new-api
    -> DeepSeek、Kimi 等模型供应商
```

| 层级 | 负责能力 |
|------|----------|
| LeetModel `ai-gateway-service` | 内部统一契约、业务场景到逻辑模型的选择、能力校验、业务调用标识、业务错误和最小审计 |
| new-api | 供应商密钥、协议转换、实际渠道选择、模型映射、渠道重试、额度计量和渠道调用日志 |

本轮只完成 new-api 的本地 Docker 部署和技术可行性验证。`ai-gateway-service` 仍按当前实现直连供应商，尚未调用 new-api。后续切换必须作为独立任务设计和验收，避免两层同时进行供应商选择或自动重试。

## 已核验接口能力

以本地 new-api `v1.0.0-rc.26` 源码为核验基线：

| 用途 | 方法与路径 | 鉴权 |
|------|------------|------|
| 服务状态 | `GET /api/status` | 无 |
| OpenAI 兼容对话 | `POST /v1/chat/completions` | Relay Token |
| OpenAI Responses | `POST /v1/responses` | Relay Token |
| 可用模型 | `GET /v1/models` | Relay Token |
| Token 额度与累计使用 | `GET /api/usage/token/` | Relay Token |
| Token 最近调用日志 | `GET /api/log/token` | Relay Token |
| OpenAI 兼容额度 | `GET /v1/dashboard/billing/subscription` | Relay Token |
| OpenAI 兼容累计用量 | `GET /v1/dashboard/billing/usage` | Relay Token |

模型调用响应包含 Token 用量，但不保证直接包含最终货币花费。精确单次扣费记录在消费日志的 `quota` 字段中，可通过响应头 `X-Oneapi-Request-Id` 与日志关联。

## 本地部署

new-api 已加入 `LeetModel-backend/docker-compose.yml`：

- 固定镜像：`calciumion/new-api:v1.0.0-rc.26`
- 访问地址：`http://localhost:3000`
- 数据库：单实例 SQLite
- 持久化卷：`new-api-data`，挂载到容器 `/data`
- 暴露范围：仅绑定本机 `127.0.0.1:3000`
- 健康检查：`GET /api/status`

启动：

```bash
cd LeetModel-backend
docker compose up -d --wait new-api
curl --fail http://localhost:3000/api/status
```

首次启动后访问 `http://localhost:3000`，按页面引导创建管理员。随后在管理后台新建供应商渠道、测试并配置模型，再创建仅供 LeetModel 使用且带模型白名单和额度限制的 Relay Token。

```bash
export NEW_API_TOKEN='sk-替换为本地创建的Token'

curl http://localhost:3000/v1/models \
  -H "Authorization: Bearer ${NEW_API_TOKEN}"

curl http://localhost:3000/v1/chat/completions \
  -H "Authorization: Bearer ${NEW_API_TOKEN}" \
  -H 'Content-Type: application/json' \
  -d '{"model":"替换为已配置模型","messages":[{"role":"user","content":"你好"}],"stream":false}'

curl http://localhost:3000/api/usage/token/ \
  -H "Authorization: Bearer ${NEW_API_TOKEN}"

curl http://localhost:3000/api/log/token \
  -H "Authorization: Bearer ${NEW_API_TOKEN}"
```

停止但保留数据使用 `docker compose stop new-api`。执行 `docker compose down -v` 会删除包括 `new-api-data` 在内的数据卷，不应在需要保留配置和日志时使用。

## 配置与安全边界

- 供应商 API Key 和 new-api Relay Token 不写入仓库。
- 本地默认 Session Secret 只用于单机开发；共享环境和生产环境必须通过 `NEW_API_SESSION_SECRET` 注入高强度随机值。
- new-api 当前只监听本机。生产环境应通过受控内网或反向代理访问，并配置 HTTPS 和访问控制。
- new-api 使用独立 SQLite 数据卷，不读写 LeetModel 业务数据库。
- 本轮部署成功只证明服务和接口基础可用；真实模型对话仍需要管理员完成首次初始化、渠道和 Token 配置。

## 后续独立任务

- 明确 LeetModel 场景到 new-api 逻辑模型的映射。
- 设计 new-api Adapter 与现有供应商 Adapter 的迁移和回退策略。
- 确定两层重试、路由、计费和审计的唯一责任方。
- 保存 `X-Oneapi-Request-Id` 与 LeetModel `callId` 的关联。
- 统一映射 new-api 不可用、额度不足、模型不存在和限流错误。
