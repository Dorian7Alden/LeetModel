## new-api 黑盒核验

> 核验日期：2026-08-28；目标：本机 `new-api v1.0.0-rc.26`。所有请求临时读取仓库外 Relay Token，本文只保存脱敏结构和结论。

### 运行基线

| 项目 | 实测结果 |
|------|----------|
| 容器 | `calciumion/new-api:v1.0.0-rc.26`，健康检查通过 |
| 监听 | `127.0.0.1:3000`，不对局域网公开 |
| 持久化 | Docker 命名卷挂载到 `/data` |
| 系统状态 | `/api/status` HTTP 200，返回 `success=true` 和版本 |
| Relay Token | 仓库外测试 Token 可认证；Token 只能在控制台创建时查看完整值 |
| 模型目录 | `/v1/models` HTTP 200，当前有 DeepSeek 文本/视觉和 Kimi 文本模型 |

当前已完成管理员初始化并存在可用渠道，否则状态、Token、模型列表和真实调用不能同时成功。渠道密钥、渠道状态修改、Relay Token 创建/撤销仍是 new-api 控制台人工管理操作；LeetModel 不调用管理 API 自动修改这些数据。模型列表是核验时运行事实，启动时仍需按项目配置校验必需模型。

### 文本 Chat

固定请求使用 `POST /v1/chat/completions`、Bearer 认证、非流式消息和最小 JSON 输出：

| 能力 | DeepSeek 实测 | Kimi 实测 | 实现约束 |
|------|---------------|-----------|----------|
| 文本消息 | HTTP 200 | HTTP 200 | OpenAI `messages` 可统一映射 |
| JSON 输出 | `response_format.type=json_object` 成功 | 同字段成功 | 保留统一枚举，不记录正文 |
| thinking | 默认返回 `reasoning_content` | `thinking.type=disabled` 后正文成功且不返回推理正文 | 按模型能力映射，不能假设默认一致 |
| temperature | `0` 成功 | 显式不兼容值返回 HTTP 400；省略后成功 | 可空参数不发送；模型配置负责合法范围 |
| maxTokens | 64 可返回正文 | 64 被推理耗尽，空正文且 `finish_reason=length`；256 成功 | 空正文或 length 不能当正常业务结果 |
| usage | 输入、输出、总量、缓存和推理明细 | 输入、输出、总量、推理明细，缓存字段可能缺失 | 缺失字段保持未知，不补零 |
| 客户端超时 | `curl --max-time 0.001` 返回 exit 28 | 同一 HTTP 客户端语义 | 映射为网关超时，不重试渠道 |

响应体稳定可用字段包括 `id`、`model`、`object`、`created`、`choices[].finish_reason`、`choices[].message.content`、可选 `reasoning_content` 和 `usage`。错误体使用 `error.message/type/param/code`；上游 message 只用于内部分类，不直接透传业务服务。

认证头缺失和无效 Token 都返回 HTTP 401、`error.type=new_api_error`。不存在的模型返回 HTTP 503、`error.code=model_not_found`。Kimi 非法参数返回 HTTP 400、`invalid_request_error`。这些状态与错误码共同参与映射，不能只匹配自然语言 message。

### 多模态 Chat

`deepseek-v4-flash-vision-exp` 接受 OpenAI 内容块：

```json
{
  "type": "image_url",
  "image_url": {"url": "https://.../synthetic.png"}
}
```

使用公开生成的 64×64 合成字母 PNG 返回 HTTP 200 并正确识别；未上传项目论文或知识库材料。1×1 data URL PNG 返回 HTTP 400、`invalid_request_error`，说明仅校验 MIME 和 Base64 长度不足以保证上游接受。正式评审可以沿用 `image_url` 映射，但必须使用项目渲染出的合法 PNG/JPEG，并把 HTTP 400 图片错误转换成媒体输入错误。

### S11 工具调用门禁

2026-08-31 使用仓库外 Relay Token 对 `deepseek-v4-flash` 执行最小只读函数工具请求，未记录 Prompt、回答、完整调用 ID 或 Token：

| 请求方式 | 结果 | 结论 |
|----------|------|------|
| `tools` + `tool_choice=auto` | HTTP 200，`finish_reason=tool_calls`，返回 1 个函数调用，函数名正确且参数为合法 JSON Object | new-api、当前渠道与模型具备首版客服所需的原生自动工具调用能力 |
| `tools` + 强制指定函数对象 | HTTP 400，`invalid_request_error` | 当前生产客服不使用强制指定方式；公共适配仍按标准 OpenAI 结构映射并由本地协议测试覆盖 |

因此只把已实测的 `NEW_API/deepseek-v4-flash` 模型能力标记为 `tools=true`，既有无工具模型执行配置仍保持 `tools=false`。后续工具版工作流必须发布独立且显式允许工具的模型执行配置，不能修改旧版本获得新能力。

### 请求关联、usage、quota 与日志

| 数据 | 同步 Chat 响应 | Token 查询 | Token 日志 | 结论 |
|------|----------------|------------|-----------|------|
| new-api 响应 ID | body `id` | 无 | 与日志 `request_id` 不是同一值 | 保存为 `providerResponseId`，不能冒充日志请求 ID |
| new-api 日志请求 ID | 未在本次响应头/体发现 | 无 | `request_id` | S2 若需稳定关联，必须另行核验响应头或由请求上下文传入；S1 不臆造映射 |
| Token | `prompt_tokens`、`completion_tokens`、`total_tokens` | 无 | 输入、输出 | 同步响应优先；日志用于异步补全 |
| 缓存 Token | DeepSeek 明细存在，Kimi 可缺失 | 无 | `other.cache_tokens` | 按来源与完整性保存 |
| 推理 Token | `completion_tokens_details.reasoning_tokens` | 无 | 本版日志未提供独立列 | 同步可得时保存，否则未知 |
| quota | Chat 响应无 | `/api/usage/token/` 提供总授予、已用、可用、无限状态 | 单次 `quota` | quota 不是货币金额，不能直接当成本 |
| 调用耗时 | 客户端可测总耗时 | 无 | `use_time` | new-api 日志粒度与 LeetModel 计时分别保存 |
| 渠道信息 | 不提供 | 不提供 | Token 日志不返回 channel ID，仅有可空渠道展示字段 | LeetModel 不依赖渠道 ID 做业务路由 |

`/api/log/token` 使用同一 Relay Token 即可查询最近日志，本次请求完成后立即可见，返回最近数组而非稳定分页契约。它包含用户名、IP、Token 名等不应进入 LeetModel 日志的字段；后续补全只选择允许字段并禁止打印原始响应。管理员全量日志 API 需要管理端身份，不属于 ai-gateway-service 运行凭据范围。

### 安全结论

- Relay Token 只从环境变量注入，不写配置默认值、测试夹具、日志或文档。
- 黑盒响应中的正文、推理正文、用户/IP/Token 名称和完整 ID 不进入仓库。
- new-api 日志是渠道治理和计费事实源；LeetModel 审计只保存业务允许的调用标识、模型、状态、用量和时间。
- S1 只实现同步 Chat 与错误映射；quota/日志异步费用补全留给 S2。

### S1 真实链路验收

2026-08-28 使用仓库外测试 Relay Token 启动 `ai-gateway-service`，并分别执行显式门控的客服文本与论文评审多模态冒烟：

| 链路 | 结果 | 模型 | 可观测结果 |
|------|------|------|------------|
| ai-assistant-service → common-ai → ai-gateway-service → new-api | 成功 | `deepseek-v4-flash` | LeetModel `callId`、new-api 响应 ID 与 usage 可得 |
| ai-review-service → common-ai → ai-gateway-service → new-api | 网关调用成功；合成空白评审材料未强求通过业务输出校验 | `deepseek-v4-flash-vision-exp` | LeetModel `callId`、new-api 响应 ID 与 usage 可得 |

冒烟测试默认关闭，只有显式设置 `RUN_NEW_API_SMOKE=true` 才访问本地网关；Relay Token 由网关进程环境注入，测试代码和业务服务均不读取密钥文件。网关日志只记录 `callId`、逻辑 provider、模型和总 Token，不记录消息、回答、推理正文或图片内容。

删除旧供应商直连后，后端 17 模块 Maven reactor 全量测试通过；网关模块包含配置绑定、适配器、Controller、服务和审计回归。当前生产 Chat 适配器只有 `NewApiAdapter`。
