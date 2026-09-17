# 三级缓存与 HTTP 协商缓存导致数据库变更后前端展示未更新

> 影响范围：`problem-service`（多级缓存与 ETag 验证器）、公开题库页面（`ProblemListPage.vue` / `ProblemList.vue`）  
> 涉及技术：Caffeine 本地缓存、Redis 集中缓存、HTTP 304 协商缓存（ETag / If-None-Match）、Flyway 数据库迁移

---

## 报错现象

在完成题目类型从 1:1 到 1:n 的数据结构改造，并执行 Flyway V12 迁移向 MySQL `problem_tag` 插入多个题型标签（如城市共享单车调度同时关联「优化」与「预测」）后：

1. **终端 curl 验证正常**：通过 `curl http://localhost:8080/api/public/problems` 直接请求网关，返回的 `tagNames` 数组中明确包含了新增的多个题型标签。
2. **浏览器页面呈现异常**：刷新题库页面（`http://localhost:5173/problem/problemListPage`），表格中该题目却**依然只显示一个题型标签**，新标签完全没有渲染出来。
3. **前端 DOM 排查**：在 Vue 组件模板中临时输出 `(item.tagNames || []).join(',')`，发现页面响应对象中的 `tagNames` 数组压根只有原先的 1 个标签，与 curl 看到的结果出现截然不同的“两面性”。

---

## 根因分析

该问题是**数据库直接写变更（Flyway 迁移）**与**生产级“三级缓存 + HTTP 协商缓存”链路**脱节所引发的经典缓存一致性与浏览器端本地缓存问题。具体包含以下两层核心原因：

### 1. HTTP ETag 协商缓存与浏览器本地磁盘缓存（304 劫持）

为了支撑题库高并发读取，`PublicProblemController.page` 实现了基于 ETag 的 HTTP 协商缓存：

```java
HttpCacheSupport.Validator validator = publicCacheService.pageValidator(query);
if (validator.matches(ifNoneMatch)) return validator.notModified(); // 返回 HTTP 304
return validator.ok(Result.ok(publicCacheService.page(query)));
```

- **验证器机制**：服务端的 `validator` 会根据当前缓存区域版本（`versionProvider.current(REGION, SCOPE)`）、逻辑 Key 哈希（`page:SHA256(...)`）与代际计算出强 ETag。
- **浏览器行为**：前端浏览器在首次访问时缓存了未做 V12 迁移前的数据，并记住了响应头中的 ETag（如 `W/"d7c28b9c...-022f359d06506d79"`）。后续刷新页面时，浏览器自动在请求头中携带：
  ```http
  If-None-Match: W/"d7c28b9c...-022f359d06506d79"
  ```
- **脱节原因**：Flyway 脚本直接在 MySQL 执行 `INSERT`，**没有经过 `ProblemService` 业务层触发缓存失效事务 Outbox 消息**，因此 Redis 中的区域版本（revision）与代际（generation）并没有递增。
- **结果**：服务端比对 ETag 认为“内容未发生改变”，直接向浏览器返回了 `HTTP/1.1 304 Not Modified`（空响应体）。浏览器拿到 304 后，直接从本地 Disk Cache / Memory Cache 中解出历史快照提供给 Vue 渲染，导致界面始终展示旧数据。
- **curl 与浏览器的差异**：curl 默认不携带 `If-None-Match` 请求头，所以直接拿到 200 响应并触发后端实时读取；而浏览器受 ETag 机制保护反而拿到了旧数据。

### 2. 多级缓存（Caffeine L1 + Redis L2）未感知直改 SQL

系统的公开题库采用了 `Caffeine 本地内存缓存 -> Redis 集中缓存 -> MySQL` 的三级缓存架构：

- 正常业务更新流程走事务，在提交后通过本地 Outbox 与 Pub/Sub 广播让本节点和集群其他节点的 Caffeine 本地缓存与 Redis 缓存失效，并推进作用域 Revision。
- 本地开发中通过 Flyway 执行 DDL/DML，属于绕过业务应用层的纯底层写入，Redis 和 JVM 进程内的 Caffeine 依然保留着未过期的旧分页 JSON 缓存对象。

---

## 排查过程与诊断手段

### 步骤 1：利用 DOM 注入辅助属性，快速定界数据源

在前端 `ProblemList.vue` 中临时增加调试输出：

```html
<span class="debug-resp">REQ: {{ lastRequestUrl }} | FIRST_TAGS: {{ JSON.stringify(lastResponseTagNames) }}</span>
```

通过浏览器页面直接观测 Vue 内部接收到的第一条题目记录的标签数组。证实前端渲染逻辑没有 Bug，而是从网络层拿到的原始数据本身就不包含新标签。

### 步骤 2：比对 curl 与带有 ETag 的 HTTP 响应状态码

分别执行无头请求与带 `If-None-Match` 头的请求：

```bash
# 无头请求：正常返回 200 与最新多题型标签
curl -i -s "http://localhost:8080/api/public/problems?page=1&pageSize=10"

# 带上浏览器请求头：复现关键突破口
curl -i -s -H 'If-None-Match: W/"d7c28b9c2b574d20af09eb24d4a4d916-r0-v1-022f359d06506d79"' "http://localhost:8080/api/public/problems?page=1&pageSize=10"
```

输出：
```http
HTTP/1.1 304 Not Modified
ETag: W/"d7c28b9c2b574d20af09eb24d4a4d916-r0-v1-022f359d06506d79"
```

证实服务端正是因为版本未变更返回了 304，浏览器被拦截在本地缓存层。

### 步骤 3：检查 Redis 缓存代际与键结构

```bash
docker exec -i leetmodel-cache-redis redis-cli keys "lm:dev:cache:*"
```

查阅到缓存中存在 `problem-service:public:all:v1:r0:page:xxxxx`，且其 Revision 停留在历史旧版本。

---

## 解决方案

### 1. 开发联调态修复：推进缓存代际并清空集中缓存

在本地执行数据库升级或测试数据插桩后，主动刷新 Redis 缓存：

```bash
docker exec -i leetmodel-cache-redis redis-cli flushdb
```

- Redis 代际键被清空后，`CacheCoordinator` 在下一次检测或重连时会重新生成全新的 `generation` UUID。
- `handleGeneration` 触发 `onGenerationChanged()` 自动广播清理本地所有节点的 Caffeine 内存缓存。
- 再次发起的请求会重新生成全新的 ETag（例如 `W/"0e3a8566...-bba7c35d19a3faf8"`）。浏览器因 ETag 失配正常获取 HTTP 200 与全量新数据。

### 2. 生产工程启示与规范防御

1. **运维修数/批量补数必须配套失效补偿**：
   - 在有三级缓存和协商缓存的系统中，任何直接通过 SQL 脚本批量修数、导入历史赛题或发布题目的操作，都必须同步调用微服务的缓存刷新管理端接口（如 `/api/admin/cache/evict`）或执行代际推进脚本，严禁只改库不刷缓存。
2. **多题型测试数据梯度设计**：
   - 本次顺带将测试数据集进一步完善，新增 `V13__add_three_problem_types_sample.sql`，构建出“单题型（1个胶囊）、双题型（2个胶囊）、三题型（3个胶囊，优化+预测+评价）”的完整测试样例梯度，并在集成测试中通过代码断言 `tagNames.containsAll(List.of("优化", "预测"))`。
