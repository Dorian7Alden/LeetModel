## LangChain4j 兼容性核验

> 核验日期：2026-08-28。S0 技术结论：维持 LangChain4j `0.34.0`，不执行 1.x 升级；RAG V1 使用自定义 `EmbeddingModel` 适配 ai-gateway-service，并使用 `langchain4j-elasticsearch:0.34.0` 连接 Elasticsearch 8.x。

### 证据范围

版本判断只使用以下上游一手资料和本地 Maven 解析结果：

- [LangChain4j 官方入门文档](https://docs.langchain4j.dev/get-started)：当前主线最低 JDK 为 17，并说明各集成使用独立 Maven 模块。
- [LangChain4j 官方发布记录](https://github.com/langchain4j/langchain4j/releases)：用于确认 1.x 的稳定核心版本与迁移跨度。
- [Maven Central 核心模块元数据](https://repo1.maven.org/maven2/dev/langchain4j/langchain4j/maven-metadata.xml) 与 [Elasticsearch 模块元数据](https://repo1.maven.org/maven2/dev/langchain4j/langchain4j-elasticsearch/maven-metadata.xml)：核验实际发布版本和集成模块后缀。
- Maven Central 中 `0.34.0`、`1.19.0` 和 `1.19.0-beta29` 的发布 POM、Jar 与源码包：核验依赖版本和实际 API，不依据二手教程推断。

### 候选兼容矩阵

| 核验项 | 当前 `0.34.0` | 候选核心 `1.19.0` / Elasticsearch `1.19.0-beta29` | 项目结论 |
|--------|----------------|----------------------------------------------------|----------|
| Java 17 | 上游字节码基线为 Java 8，可在项目 Java 17 编译运行 | 官方主线最低 JDK 17 | 两者满足；升级不带来本项目必要收益 |
| Spring Boot 3.3.5 | 项目只用核心库，不引入 LangChain4j Spring Starter；实测 Maven reactor 通过 | 核心可用于 Java 17，但 1.19.0 带入 Jackson 2.22.x，与 Boot 3.3.5 管理线存在更大依赖漂移 | 维持 0.34.0，避免无收益的依赖面扩大 |
| `EmbeddingModel` | `embedAll(List<TextSegment>)` 可由项目确定性实现或网关适配器实现 | 增加 request/response、listener、provider 等新 API，旧便捷方法仍作为 default 方法存在 | 0.34.0 已满足自定义适配，升级会增加迁移与回归面 |
| `EmbeddingStoreIngestor` | 已存在 Builder，可组合 splitter、model 和 store | 仍存在，包名保持 | 无升级必要 |
| `EmbeddingStoreContentRetriever` | 已存在，支持 Top K、最小分数和过滤器 | 仍存在，包名保持 | 无升级必要 |
| 内存 Store | `InMemoryEmbeddingStore<TextSegment>` 可用于确定性测试 | 仍提供 | 两者满足 |
| Elasticsearch Store | `langchain4j-elasticsearch:0.34.0` 使用 Elasticsearch Java Client `8.14.3` | 同版本集成模块发布为 `1.19.0-beta29`，使用客户端 `9.3.1` | 正式目标是 Elasticsearch 8.x，选择 0.34.0 同代客户端 |
| OpenAI 自定义 Base URL | `langchain4j-open-ai` Builder 支持 `baseUrl` | 仍支持 | Chat/Embedding 实际仍统一走 common-ai 与 ai-gateway-service，不允许业务服务借此直连 new-api |
| `PromptTemplate` | ai-review-service 当前代码已使用并通过编译 | 类仍存在，但主版本升级仍需全量回归 | 不为 RAG 单独升级，避免扰动现有评审 |

候选 1.x 的核心 artifact 使用稳定版本号，但 Elasticsearch 集成仍采用与核心配套的 beta 后缀，并已转向 Elasticsearch 9.x 客户端。项目当前既不需要 1.x 新 Agent 能力，也没有 Elasticsearch 9.x 目标，因此“版本更新”本身不是升级理由。

### 0.34.0 编译实验

隔离测试 `LangChain4jRagCompatibilityTest` 覆盖以下链路：

```text
Markdown 字符串
  → Document
  → DocumentSplitters.recursive
  → EmbeddingStoreIngestor
  → 自定义确定性 EmbeddingModel
  → InMemoryEmbeddingStore
  → EmbeddingStoreContentRetriever Top 1
```

测试使用关键词向量，不调用外部模型，也不进入客服正式工作流。执行命令：

```bash
cd LeetModel-backend
mvn -pl ai-review-service -am \
  -Dtest=LangChain4jRagCompatibilityTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

2026-08-28 实测结果为 `Tests run: 1, Failures: 0, Errors: 0`，reactor 六个模块 `BUILD SUCCESS`。这证明 0.34.0 已具备项目所需的切分、写入、召回和自定义 Embedding 扩展点；S0-03 升级实验因此不适用。

### 固化技术基线

| 能力 | 固定选择 |
|------|----------|
| LangChain4j 核心 | `dev.langchain4j:langchain4j:0.34.0` |
| Elasticsearch 集成 | `dev.langchain4j:langchain4j-elasticsearch:0.34.0`，由父 POM 统一管理，消费者在 S4 按需声明 |
| 正式向量库 | Elasticsearch 8.x；具体镜像小版本在 S4 部署任务锁定，并与 8.14.3 Java Client 做真实集成测试 |
| 自动化测试 Store | `InMemoryEmbeddingStore`，不作为生产降级存储 |
| Embedding 接入 | 项目实现 `EmbeddingModel`，内部通过 common-ai → ai-gateway-service → new-api 调用；不在业务服务使用 LangChain4j OpenAI 客户端直连 |
| 文档切分与摄取 | `DocumentSplitters` + `EmbeddingStoreIngestor` |
| 在线召回 | `EmbeddingStoreContentRetriever` |

后续不得在单个功能任务中自行切换到 Spring AI、其他向量框架或 LangChain4j 1.x。只有出现 0.34.0 无法修复的缺陷、必要 API 缺失、Elasticsearch 8.x 不再满足部署约束或明确安全问题时，才建立独立升级任务并重新执行兼容矩阵、评审回归和 Elasticsearch 集成测试。

### 已知风险

- `0.34.0` 已不是上游当前版本，长期安全与缺陷修复需要项目自行关注；这是一项受控技术债，不代表永久禁止升级。
- 8.14.3 客户端与最终 Elasticsearch 8.x 服务端仍需在 S4 做真实握手、索引映射、写入、过滤和召回测试，当前编译实验不能替代服务端兼容验收。
- 自定义 Embedding 适配器必须校验向量维度、批量部分失败、超时和用量，不得把本次确定性测试实现复制到生产代码。
- LangChain4j OpenAI 模块虽支持自定义 Base URL，但使用它直连 new-api 会绕过统一网关职责，正式实现禁止采用该路径。
