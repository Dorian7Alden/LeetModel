## 客服 SSE 流式协议与事件透出规范

> 实施状态：已落地。定义 AI 客服 Server-Sent Events (SSE) 流式传输格式、工具动态状态通知与前端打字机渲染规范。

---

### 1. 通信选型与端点设计

AI 客服为典型的“客户端提问一次、服务端持续输出多阶段状态与打字机文本”场景。相较于 WebSocket，采用 SSE (Server-Sent Events) 具备以下优势：
- **协议简洁**：基于标准 HTTP 协议，天然穿越网关与反向代理，无协议升级成本；
- **自动保活与轻量**：单向服务端推送，复用 HTTP/2 多路复用连接；
- **优雅降级**：与现有的短连接同步接口共享幂等校验与数据持久化逻辑。

#### 端点定义
- **主端点 (POST)**：`/api/assistant/conversations/{conversationId}/messages/stream`
  - `Accept: text/event-stream`
  - 请求体：`{ "content": "提问文本", "clientRequestId": "uuid" }`
- **辅助端点 (GET)**：`/api/assistant/conversations/{conversationId}/messages/stream?content=...&clientRequestId=...`
  - 适配原生浏览器 `EventSource` 请求。

---

### 2. 标准事件格式定义

事件流由五类标准结构化事件组成：

#### 1. `tool_start`（工具开始调用）
模型发起工具调用时即刻推送，前端呈现动态“正在执行”工具指示条：
```text
event: tool_start
data: {"tool":"search_problem","displayName":"正在检索题目库...","status":"RUNNING"}
```

#### 2. `tool_end`（工具执行完成）
工具执行结束并产出事实后推送，携带快照供前端提前解析卡片或收折状态条：
```text
event: tool_end
data: {"tool":"search_problem","displayName":"已检索到 2 道相关题目","status":"COMPLETED","toolContextJson":"..."}
```

#### 3. `delta`（文本增量片段）
大模型生成文本片段，驱动前端打字机平滑流式吐字：
```text
event: delta
data: {"content":"针对你提到的层次分析法，"}
```

#### 4. `message_end`（回复生成完毕）
整条消息持久化落库后推送，包含最终持久化消息实体与卡片上下文：
```text
event: message_end
data: {"messageId":1024,"status":"COMPLETED","fullContent":"...","toolContextJson":"..."}
```

#### 5. `error`（流式异常）
执行过程中发生超时或业务异常时推送：
```text
event: error
data: {"code":50501,"message":"AI 客服暂时无法回答，请稍后重试"}
```

---

### 3. 前端交互与容灾降级

1. **打字机与工具条折叠**：
   - 收到 `tool_start` 时在回复气泡顶部挂载小巧的工具进度指示条；
   - 收到 `tool_end` 时切换为已完成徽章；
   - 收到 `delta` 时动态拼接字符并通过 Markdown 增量渲染，视口随文本自然向下滚动；
   - 收到 `message_end` 时固化为最终卡片与元数据。
2. **断连容灾与自动回退**：
   - 若浏览器不支持 `fetch` ReadableStream 或环境禁用 SSE，前端自动无缝回退到 `POST /api/assistant/conversations/{id}/messages` 同步接口；
   - 若在流接收过程中发生意外网络断开，服务端已通过幂等键保障单条消息落库，用户刷新或重试不会产生脏数据。
