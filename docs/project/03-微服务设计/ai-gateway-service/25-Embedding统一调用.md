# Embedding 统一调用

> S3 实现基线。业务服务只能通过 `common-ai` 调用 `ai-gateway-service`，不得持有 new-api 地址、渠道名或 Relay Token。

## common-ai 契约

- `AiEmbeddingRequest.logicalModel` 是网关管理的稳定逻辑模型名，不是供应商模型名。
- 单条输入使用与批量相同的 `inputs` 数组结构；公共契约最多 128 条、每条最多 32768 个字符，网关按逻辑模型继续施加更严格的批量和总量限制。
- `AiCallContext` 必填。RAG 建库使用 `RAG/INDEX_DOCUMENTS`，在线查询使用 `RAG/RETRIEVE_CONTEXT`，网关只把业务 ID 当作不透明关联键。
- `AiEmbeddingResponse` 返回 LeetModel `callId`、逻辑模型、实际模型、统一维度、按输入索引排列的向量、usage 和费用。
- 向量值必须是有限浮点数；同一响应的向量维度必须一致，索引不得重复。Token 或费用不可得时继续使用统一的 UNKNOWN 完整性语义。

请求与响应均不包含 Base URL、渠道标识或密钥。原文和向量只在调用链中传输，不进入审计日志；审计仅保存条数、维度、用量、费用、耗时和调用标识。

`AiClient.embed` 固定调用 `/internal/ai/embeddings`。现有 Chat-only 实现继续保持函数式接口兼容，默认明确返回“不支持 Embedding”；正式 `HttpAiClient` 覆盖该方法，并把业务失败、空响应、HTTP 失败和连接/读取超时统一转换为 `AiClientException`，异常消息不拼接请求原文。
