## 输出契约与 Markdown 报告

> 设计状态：已确认设计，尚未实现。字段名是实现依据草案。


### 一、顶级结构

```json
{
  "workflowVersion": "GROUNDED_SUGGESTION_V4",
  "resultSchemaVersion": "GROUNDED_SUGGESTION_V4",
  "overallStrategyMarkdown": "## 本轮完善主线\\n...",
  "topPriorities": [],
  "items": [],
  "knowledgeBasis": [],
  "subTaskSummaries": []
}
```


### 二、顶部优先事项

```json
{
  "suggestionId": "S-1",
  "title": "补充贝叶斯反演参数关系说明",
  "guidanceType": "COMPLETENESS_ENHANCEMENT",
  "priority": "P1",
  "summaryMarkdown": "当前后验推断同时涉及 $n$ 与 $t$，但二者的处理关系没有完整交代。"
}
```

顶部只展示 1 至 3 项，必须引用完整建议项，不复制另一份语义不同的短文案。


### 三、建议项

```json
{
  "suggestionId": "S-1",
  "priority": "P1",
  "guidanceType": "COMPLETENESS_ENHANCEMENT",
  "category": "MODEL",
  "subProblemNo": 1,
  "title": "补充未知参数关系与可识别性说明",
  "currentStateMarkdown": "当前似然函数同时包含未知量 $n$ 与 $t$。",
  "rationaleMarkdown": "如果不说明固定量、先验或边际化关系，读者难以判断后验是否可识别。",
  "guidanceMarkdown": "建议补充 $n$ 与 $t$ 的处理关系、各自信息来源以及该关系对结果不确定性的影响。",
  "applicabilityMarkdown": "该方向适用于论文希望保留现有贝叶斯反演框架的情况。",
  "targetLocation": {
    "physicalPages": [11],
    "section": "Bayesian Inversion Framework",
    "anchorBlockIds": ["B146"]
  },
  "evidenceQuotes": [],
  "acceptanceCriteriaMarkdown": [
    "读者能够区分哪些参数来自观测、哪些参数由先验给定。",
    "论文说明参数关系对后验区间和最终结论的影响。"
  ],
  "evidenceChain": {
    "paperEvidenceIds": ["B146"],
    "reviewFindingIds": ["F-Q1-003"],
    "knowledgeCitationIds": ["KC-018"]
  }
}
```


### 四、可选探索方向

`OPTIONAL_EXPLORATION` 可以附带多个并列方向：

```json
{
  "explorationDimensions": [
    {
      "title": "增加简单基准对照",
      "purposeMarkdown": "用于判断现有复杂模型带来的实际增益。",
      "applicabilityMarkdown": "适用于当前已有可复用训练与测试数据的情况。",
      "tradeoffMarkdown": "会增加实验工作量，但不要求替换现有模型。"
    }
  ]
}
```

方向数量建议为 1 至 3 个，不能变成算法清单堆砌。


### 五、原文与知识依据

`evidenceQuotes` 由服务端根据 `paperEvidenceIds` 确定性生成，规则与评审 V4 相同。

`knowledgeBasis` 保存：

- 标题、章节和稳定路径；
- 支撑结论 Markdown；
- 对当前论文的适用性 Markdown；
- 资料版本和内容哈希。

前端必须把知识依据展示为“为什么这种完善方向合理”，不得把它显示成论文事实。


### 六、Markdown 字段

以下字段支持 Markdown 和 KaTeX：

- `overallStrategyMarkdown`
- `summaryMarkdown`
- `currentStateMarkdown`
- `rationaleMarkdown`
- `guidanceMarkdown`
- `applicabilityMarkdown`
- `acceptanceCriteriaMarkdown`
- 探索方向的目的、适用性和代价
- 原文摘录和知识依据

标题、枚举、ID、页码和排序字段保持纯结构化值。


### 七、媒体边界

- V4 不允许模型生成图片 URL。
- Markdown 图片只能引用服务端验证过的内部资产。
- 最小实现展示图号、图题、页码和解析图像描述。
- 实际裁图、标注和生成对比图属于后续独立能力。
