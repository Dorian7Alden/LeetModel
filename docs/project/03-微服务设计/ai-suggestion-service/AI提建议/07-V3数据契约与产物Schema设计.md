# AI 论文建议 V3 数据契约与产物 Schema 设计

> 本文档规范第三代 AI 论文建议（`GROUNDED_SUGGESTION_V3`）的输出契约、JSON Schema、各字段物理语义、Markdown 富文本规范及前端交互契约。

---

## 一、设计目标与产物演进

历史版本（`GROUNDED_SUGGESTION_V2`）将建议动作简化为字符串数组 `actions: ["一句话"]`，无法承载“公式修改、算法调优伪代码、参数推荐表与对比图表”等专业数模深度内容。

`GROUNDED_SUGGESTION_V3` 的核心演进目标是：
1. **结构化容器包裹 Markdown 富文本**：外层通过强类型 JSON 维护建议编号、优先级、题型小问和依据链；内层核心修改方案 `actionPlanMarkdown` 输出为结构清晰、排版优雅的标准 Markdown 富文本。
2. **区分改错（CORRECTION）与升华（ADVANCEMENT）**：明确每条建议的性质定位。
3. **物理定位精细化**：对接 `PAPER_DOCUMENT_V2` 的 `anchorBlockIds` 与物理页码 `physicalPages`。

---

## 二、产物 JSON Schema 契约定义

### 2.1 顶级 Schema 结构

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "title": "GroundedSuggestionV3Output",
  "type": "object",
  "required": [
    "workflowVersion",
    "overallStrategy",
    "topPriorities",
    "items"
  ],
  "properties": {
    "workflowVersion": {
      "type": "string",
      "const": "GROUNDED_SUGGESTION_V3"
    },
    "overallStrategy": {
      "type": "string",
      "description": "专家组长视角的本轮修改全局总体策略与方向指引 (Markdown 格式)"
    },
    "topPriorities": {
      "type": "array",
      "description": "首页突出展示的最优先实施的 3 项关键改动摘要",
      "minItems": 1,
      "maxItems": 3,
      "items": { "type": "string" }
    },
    "subTaskSummaries": {
      "type": "array",
      "description": "各子任务推演执行摘要",
      "items": {
        "type": "object",
        "required": ["taskId", "taskType", "taskName", "status", "suggestionCount"],
        "properties": {
          "taskId": { "type": "string" },
          "taskType": { "type": "string" },
          "taskName": { "type": "string" },
          "status": { "type": "string", "enum": ["SUCCESS", "DEGRADED", "FAILED"] },
          "suggestionCount": { "type": "integer" }
        }
      }
    },
    "items": {
      "type": "array",
      "description": "经过全局去重与科学优先级排序后的全量结构化建议项",
      "minItems": 1,
      "maxItems": 16,
      "items": { "$ref": "#/$defs/SuggestionItem" }
    }
  },
  "$defs": {
    "SuggestionItem": {
      "type": "object",
      "required": [
        "suggestionId",
        "priority",
        "type",
        "category",
        "title",
        "problemOrGap",
        "targetLocation",
        "actionPlanMarkdown",
        "acceptanceCriteria",
        "evidenceChain"
      ],
      "properties": {
        "suggestionId": {
          "type": "string",
          "pattern": "^S-[1-9][0-9]*$",
          "description": "从 S-1 开始严格自增的唯一建议编号"
        },
        "priority": {
          "type": "string",
          "enum": ["P0", "P1", "P2", "P3"],
          "description": "优先级: P0 阻断硬伤, P1 关键失分与核心升华, P2 进阶优化, P3 规范排版"
        },
        "type": {
          "type": "string",
          "enum": ["CORRECTION", "ADVANCEMENT"],
          "description": "建议性质: CORRECTION 修复评审缺陷, ADVANCEMENT 模型拔高升华"
        },
        "category": {
          "type": "string",
          "enum": [
            "PROBLEM", "ASSUMPTION", "DATA", "MODEL",
            "SOLUTION", "RESULT", "VALIDATION", "SENSITIVITY",
            "WRITING", "FIGURE", "CITATION", "APPENDIX"
          ]
        },
        "subProblemNo": {
          "type": ["integer", "null"],
          "description": "关联的小题题号 (0 表示全篇结构或不对应单一小问)"
        },
        "title": {
          "type": "string",
          "description": "简明扼要的建议主题 (如: 第1问非线性容量约束线性化改写)"
        },
        "problemOrGap": {
          "type": "string",
          "description": "当前论文存在的具体缺陷、硬伤或对照国奖标准的提升空间"
        },
        "diagnosis": {
          "type": "string",
          "description": "深度技术病因分析 (为什么当前模型有缺陷或为什么当前方法过于平庸)"
        },
        "targetLocation": {
          "type": "object",
          "required": ["physicalPages", "section"],
          "properties": {
            "physicalPages": {
              "type": "array",
              "items": { "type": "integer" }
            },
            "section": { "type": "string" },
            "anchorBlockIds": {
              "type": "array",
              "items": { "type": "string" }
            }
          }
        },
        "actionPlanMarkdown": {
          "type": "string",
          "description": "操作手册级修改指导正文 (Markdown 格式，含公式、代码块、参数推荐表)"
        },
        "acceptanceCriteria": {
          "type": "array",
          "minItems": 1,
          "items": { "type": "string" },
          "description": "可观察、可量化验证的验收标准清单"
        },
        "evidenceChain": {
          "type": "object",
          "required": ["paperEvidenceIds", "reviewFindingIds", "knowledgeCitationIds"],
          "properties": {
            "paperEvidenceIds": {
              "type": "array",
              "minItems": 1,
              "items": { "type": "string" }
            },
            "reviewFindingIds": {
              "type": "array",
              "items": { "type": "string" }
            },
            "knowledgeCitationIds": {
              "type": "array",
              "minItems": 1,
              "items": { "type": "string" }
            }
          }
        }
      }
    }
  }
}
```

---

## 三、核心字段 Markdown 富文本规范

每条建议的 `actionPlanMarkdown` 必须按照以下结构化子段落组织，严禁只输出一段笼统叙述：

### 3.1 规范样例
```markdown
#### 1. 原文现状与缺陷分析
当前公式 (3) 中将车辆容量设为常数 $C=100$，未引入动态时间下标 $t$，无法表达高峰时段与平峰时段的不同装载上限。

#### 2. 数学模型修正方案
建议将容量约束修正为分时段动态容量约束，并补充如下线性化表达式：
$$\sum_{i \in V} \sum_{j \in V} x_{ijt} \cdot d_i \le C_t, \quad \forall t \in T$$
其中 $C_t = C_0 \cdot \gamma_t$，$\gamma_t$ 为时段调节系数。

#### 3. 算法实现与代码参考
```python
# 在 docplex 优化模型中补充动态容量约束
for t in T:
    mdl.add_constraint(
        mdl.sum(x[i, j, t] * demand[i] for i in V for j in V) <= capacity[t],
        ctname=f"dynamic_cap_{t}"
    )
```

#### 4. 参数设置与推荐图表
- **参数推荐**：设定求解器 Cplex 时间限制为 300 秒，相对 MIP 间隙设为 `1e-4`。
- **推荐图表**：绘制各时段车辆容量占用率阶梯图，直观展现高峰平谷的承载差异。
```

---

## 四、前后端渲染与存储对齐

1. **后端实体映射**：
   - 新增 `GroundedSuggestionV3Output.java` 作为对应 DTO。
   - 数据库存储：任务的 `result_json` 字段保存全量压缩 JSON，`result_schema_version` 标记为 `GROUNDED_SUGGESTION_V3`。
2. **前端渲染契约**：
   - 前端消费 `SuggestionVO` 时，直接提取 `actionPlanMarkdown` 传入统一的 `renderSafeMarkdown` 组件进行渲染。
   - 包含的 LaTeX 公式由 KaTeX 自动排版（支持行内 `$x_i$` 与独立块级 `$$formula$$`）。
   - 代码块由 Highlight.js 渲染高亮并提供复制按钮。
3. **向后兼容性**：
   - 历史 `GROUNDED_SUGGESTION_V2` 和 `IMPROVEMENT_V1` 继续原样保持可读，前端根据 `resultSchemaVersion` 分支自适应展示。
