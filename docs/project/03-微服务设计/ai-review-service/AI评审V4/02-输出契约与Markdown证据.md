## 输出契约与 Markdown 证据

> 设计状态：已确认设计，尚未实现。字段名是实现依据草案，最终以 DTO、Flyway 和测试为准。


### 一、顶级输出

```json
{
  "workflowVersion": "DEEP_EVIDENCE_REVIEW_V4",
  "resultSchemaVersion": "DEEP_EVIDENCE_REVIEW_V4",
  "score": 82.5,
  "scoreNature": "PLATFORM_TRAINING_SCORE",
  "overallAssessmentMarkdown": "## 综合评审\\n...",
  "dimensions": [],
  "findings": [],
  "requirementCoverage": [],
  "knowledgeBasis": [],
  "scoringRule": {}
}
```

`result_json` 继续保存终态热点报告。阶段一、规划和阶段二原始快照继续独立保存，供排障和评价使用。


### 二、维度契约

```json
{
  "dimensionCode": "DIM_MATHEMATICAL_MODELING",
  "dimensionName": "数学形式化建模推导",
  "score": 20.0,
  "maxScore": 25.0,
  "reasonMarkdown": "该维度的主要优势是...\\n\\n主要限制是...",
  "strengthFindingIds": ["F-Q1-001"],
  "issueFindingIds": ["F-Q1-002"]
}
```

`reasonMarkdown` 必须引用该维度真实 Findings，不允许 Reducer 使用对所有论文相同的固定套话。


### 三、Finding 契约

```json
{
  "findingId": "F-Q1-002",
  "findingType": "ISSUE",
  "priority": "P1",
  "importance": "HIGH",
  "dimensionCode": "DIM_MATHEMATICAL_MODELING",
  "category": "MODEL",
  "title": "贝叶斯反演中的未知参数关系未完整说明",
  "explanationMarkdown": "论文同时使用未知量 $n$ 与 $t$，但当前似然函数没有说明二者如何解耦。",
  "whyItMattersMarkdown": "这会使后验估计的可识别性不清楚，读者无法判断结果由数据还是预设参数主导。",
  "scoreImpact": "-0.8 分",
  "physicalPage": 11,
  "anchorBlockIds": ["B146"],
  "evidenceQuotes": [],
  "knowledgeBasisIds": ["KB-3"]
}
```

`findingType` 仅允许：

- `STRENGTH`：论文已经真实做到且对质量有积极贡献；
- `ISSUE`：存在确定性错误、缺失、矛盾或有证据支持的重要限制。

不得把“未发现明显错误”包装成 `STRENGTH`。


### 四、原文证据契约

```json
{
  "evidenceId": "PE-001",
  "blockId": "B146",
  "physicalPage": 11,
  "sectionTitle": "Bayesian Inversion Framework",
  "blockType": "FORMULA",
  "quoteMarkdown": "$$L(t\\mid V_{obs}) = \\exp\\left(-\\frac{(V_{obs}-V_{model}(t))^2}{2\\sigma^2}\\right)$$",
  "contentHash": "sha256:...",
  "truncated": false
}
```

生成规则：

- 模型只返回真实 `blockId`，不返回 `quoteMarkdown`。
- 服务端读取当前评审锁定的 `parseArtifactId`，按块类型生成 Markdown。
- `FORMULA` 使用块级 KaTeX；`TABLE` 保留安全 HTML；`CODE` 使用带语言代码围栏；
  `FIGURE` 使用图题、图号和受信描述；普通正文使用引用块。
- 普通段落摘录默认不超过 1,200 字符，保留完整句子边界并标记 `truncated`。
- 保存 `contentHash`，证明摘录来自不可变解析块。


### 五、知识依据契约

知识依据不是论文原文，也不直接决定分数：

```json
{
  "basisId": "KB-3",
  "citationId": "KC-018",
  "title": "贝叶斯反演可识别性检查",
  "section": "参数与先验",
  "sourcePath": "数学建模/论文评审/评审板块/...",
  "supportMarkdown": "当多个未知参数只以乘积形式进入似然函数时，应说明先验、固定量或边际化策略。",
  "applicabilityMarkdown": "适用于当前论文同时估计人流量与时间参数的场景。"
}
```


### 六、Markdown 安全约束

- JSON 外层始终是结构化对象，Markdown 只存在于明确字段。
- 禁止模型输出 `<script>`、`iframe`、事件属性和任意外部图片 URL。
- V4 允许的图片证据只来自内部受信资产；最小闭环只交付图片描述。
- 前端必须经过 DOMPurify 清洗，KaTeX 使用 `throwOnError: false`。
- Markdown 字段解析失败时展示原始文本，不得导致报告白屏。
