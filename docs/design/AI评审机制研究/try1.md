

# LeetModel AI 生成题目功能设计文档

---

## 目录

- [一、功能概述](#一功能概述)
- [二、整体流程架构](#二整体流程架构)
- [三、题目数据模型](#三题目数据模型)
- [四、标签随机选取策略](#四标签随机选取策略)
- [五、提示词工程设计](#五提示词工程设计)
- [六、API 接口设计](#六api-接口设计)
- [七、核心服务架构](#七核心服务架构)
- [八、AI 响应解析与存储流程](#八ai-响应解析与存储流程)
- [九、异常处理与降级策略](#九异常处理与降级策略)
- [十、快速开发路线图](#十快速开发路线图)

---

## 一、功能概述

### 1.1 功能定位

AI 生成题目是平台**题库冷启动**的核心能力，面向管理员/出题者，通过随机标签组合 + 大模型生成，快速产出高质量的数学建模练习题目，填充初期题库。

### 1.2 核心流程一句话

> **随机抽取标签组合 → 构建结构化提示词 → 调用火山引擎大模型 → 解析结构化响应 → 存储题目并绑定标签**

### 1.3 功能边界（Demo 阶段）

| 包含 | 不包含 |
|------|--------|
| ✅ 全随机生成题目 | ❌ 分角色专项题目生成 |
| ✅ 指定部分标签 + 其余随机 | ❌ AI 生成配套数据集文件 |
| ✅ 结构化题目内容（标题/背景/子问题） | ❌ AI 生成标准答案/评分标准 |
| ✅ 生成后自动绑定标签 | ❌ 人工审核工作流 |
| ✅ 生成记录留痕（prompt/模型信息） | ❌ 批量生成队列 |

---

## 二、整体流程架构

### 2.1 时序流程

```
┌──────────┐     ┌──────────────┐     ┌────────────────┐     ┌──────────────┐     ┌────────────┐
│  前端     │     │  Controller  │     │  ProblemGenSvc  │     │  AI Service  │     │  DB        │
│  (管理端) │     │              │     │  (生成服务)     │     │  (火山引擎)   │     │            │
└────┬─────┘     └──────┬───────┘     └───────┬────────┘     └──────┬───────┘     └─────┬──────┘
     │                  │                     │                     │                   │
     │  1.请求生成题目   │                     │                     │                   │
     │  (可选标签覆盖)  │                     │                     │                   │
     │─────────────────►│                     │                     │                   │
     │                  │  2.调用生成服务       │                     │                   │
     │                  │────────────────────►│                     │                   │
     │                  │                     │                     │                   │
     │                  │                     │  3.查询可用标签       │                   │
     │                  │                     │─────────────────────────────────────────►│
     │                  │                     │◄─────────────────────────────────────────│
     │                  │                     │                     │                   │
     │                  │                     │  4.执行标签随机选取   │                   │
     │                  │                     │  (结合用户指定标签)   │                   │
     │                  │                     │────┐                │                   │
     │                  │                     │    │ TagSelector    │                   │
     │                  │                     │◄───┘                │                   │
     │                  │                     │                     │                   │
     │                  │                     │  5.构建提示词         │                   │
     │                  │                     │────┐                │                   │
     │                  │                     │    │ PromptBuilder  │                   │
     │                  │                     │◄───┘                │                   │
     │                  │                     │                     │                   │
     │                  │                     │  6.调用大模型 API     │                   │
     │                  │                     │────────────────────►│                   │
     │                  │                     │                     │  (生成中...)       │
     │                  │                     │  7.返回生成结果       │                   │
     │                  │                     │◄────────────────────│                   │
     │                  │                     │                     │                   │
     │                  │                     │  8.解析 JSON 响应     │                   │
     │                  │                     │────┐                │                   │
     │                  │                     │    │ ResponseParser │                   │
     │                  │                     │◄───┘                │                   │
     │                  │                     │                     │                   │
     │                  │                     │  9.存储题目+绑定标签  │                   │
     │                  │                     │─────────────────────────────────────────►│
     │                  │                     │  10.更新 usage_count │                   │
     │                  │                     │─────────────────────────────────────────►│
     │                  │                     │◄─────────────────────────────────────────│
     │                  │                     │                     │                   │
     │                  │  11.返回生成结果      │                     │                   │
     │                  │◄────────────────────│                     │                   │
     │  12.展示生成题目  │                     │                     │                   │
     │◄─────────────────│                     │                     │                   │
     │                  │                     │                     │                   │
```

### 2.2 核心模块拆解

```
┌─────────────────────────────────────────────────────────────┐
│                    ProblemGenerateService                     │
│                                                              │
│  ┌──────────────┐  ┌──────────────┐  ┌───────────────────┐  │
│  │ TagSelector   │  │ PromptBuilder│  │ ResponseParser    │  │
│  │              │  │              │  │                   │  │
│  │ - 标签随机选取 │  │ - 系统提示词  │  │ - JSON 解析       │  │
│  │ - 用户指定合并 │  │ - 用户提示词  │  │ - 字段校验        │  │
│  │ - 组合合理性  │  │ - 标签上下文  │  │ - Problem 实体映射 │  │
│  │   校验       │  │ - 输出格式约束 │  │ - 异常降级        │  │
│  └──────┬───────┘  └──────┬───────┘  └─────────┬─────────┘  │
│         │                 │                     │            │
│         ▼                 ▼                     ▼            │
│  ┌─────────────────────────────────────────────────────┐    │
│  │               AiClientService                        │    │
│  │  (火山引擎 API 封装：调用、重试、超时、Token 管理)     │    │
│  └─────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────┘
```

---

## 三、题目数据模型

### 3.1 题目表 `problem`

```sql
CREATE TABLE `problem` (
    `id`                BIGINT          NOT NULL AUTO_INCREMENT              COMMENT '题目ID',
    `title`             VARCHAR(200)    NOT NULL                             COMMENT '题目标题',
    `background`        TEXT            NOT NULL                             COMMENT '背景描述',
    `content`           TEXT            NOT NULL                             COMMENT '题目正文（完整题面，含所有子问题的 Markdown）',
    `questions`         JSON            DEFAULT NULL                         COMMENT '结构化子问题列表（JSON 数组）',
    `data_description`  TEXT            DEFAULT NULL                         COMMENT '数据说明（数据格式、字段含义等）',
    `hints`             TEXT            DEFAULT NULL                         COMMENT '解题提示（建议方法与思路）',

    `source_type`       TINYINT         NOT NULL DEFAULT 0                   COMMENT '来源类型：0-AI生成 1-人工录入 2-赛题改编',
    `status`            TINYINT         NOT NULL DEFAULT 0                   COMMENT '状态：0-草稿 1-已发布 2-已下架',

    `ai_prompt`         TEXT            DEFAULT NULL                         COMMENT 'AI 生成时的完整提示词（留痕调试）',
    `ai_model`          VARCHAR(100)    DEFAULT NULL                         COMMENT 'AI 模型标识（如 doubao-pro-256k）',
    `ai_tag_snapshot`   JSON            DEFAULT NULL                         COMMENT '生成时选取的标签快照（JSON）',

    `view_count`        INT             NOT NULL DEFAULT 0                   COMMENT '浏览次数',
    `submit_count`      INT             NOT NULL DEFAULT 0                   COMMENT '提交次数',
    `favorite_count`    INT             NOT NULL DEFAULT 0                   COMMENT '收藏次数',

    `created_by`        BIGINT          DEFAULT NULL                         COMMENT '创建者用户ID',
    `created_at`        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP   COMMENT '创建时间',
    `updated_at`        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    PRIMARY KEY (`id`),
    KEY `idx_status` (`status`),
    KEY `idx_source_type` (`source_type`),
    KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='题目表';
```

### 3.2 字段详细说明

| 字段 | 用途 | 示例值 |
|------|------|--------|
| `title` | 前端列表展示的标题 | `"城市交通信号灯配时优化问题"` |
| `background` | 题目的背景与场景描述 | 200-500 字的场景描述 |
| `content` | 完整题面 Markdown | 包含背景 + 所有问题的完整文本 |
| `questions` | 结构化子问题 | `[{"number":1,"content":"...","points":"..."}]` |
| `data_description` | 数据说明 | 描述附带数据的格式，或说明需自行采集 |
| `hints` | 解题提示 | 建议使用的模型方法 |
| `ai_prompt` | 生成时的 prompt | 留痕，方便排查质量问题和迭代 prompt |
| `ai_model` | 使用的模型版本 | `"doubao-pro-256k"` |
| `ai_tag_snapshot` | 标签快照 | `{"difficulty":"困难","problem_type":["连续建模","微分方程建模"]}` |

### 3.3 `questions` JSON 结构定义

```json
[
  {
    "number": 1,
    "content": "请建立城市主干道交叉路口的交通流量模型，分析早晚高峰时段的车辆到达规律。",
    "points": "排队论、泊松过程"
  },
  {
    "number": 2,
    "content": "基于问题一的模型，设计信号灯配时优化方案，以最小化车辆平均等待时间为目标。",
    "points": "线性规划、多目标优化"
  },
  {
    "number": 3,
    "content": "考虑行人过街需求和紧急车辆优先通行的约束，对你的优化方案进行改进。",
    "points": "约束优化、鲁棒性分析"
  }
]
```

### 3.4 `ai_tag_snapshot` JSON 结构定义

```json
{
  "difficulty": {
    "id": 1010003,
    "name": "中等",
    "code": "difficulty_medium"
  },
  "problem_type": [
    {"id": 5010004, "name": "运筹优化", "level": "L1"},
    {"id": 5020015, "name": "排队系统", "level": "L2"},
    {"id": 5030037, "name": "排队建模", "level": "L3"}
  ],
  "model_category": [
    {"id": 6010001, "name": "优化模型", "level": "L1"},
    {"id": 6020001, "name": "数学规划", "level": "L2"},
    {"id": 6030001, "name": "线性规划", "level": "L3"}
  ],
  "industry": [
    {"id": 7010001, "name": "交通运输"}
  ],
  "data_feature": [
    {"id": 8010003, "name": "时间序列数据"}
  ]
}
```

> **为什么存标签快照？** 标签可能后续被修改或禁用，快照确保每道题目的生成上下文可追溯。

---

## 四、标签随机选取策略

### 4.1 选取规则总览

| 分类 | 选取方式 | 数量 | 优先级 | 说明 |
|:---:|:---:|:---:|:---:|------|
| 难度等级 | 随机 1 个 | 1 | **必选** | 用户可指定覆盖 |
| 角色方向 | 固定 `综合` | 1 | **固定** | 通用题目默认综合 |
| 题目类型 | 随机选路径 | 1 条完整路径 | **必选** | L1→L2→L3 链 |
| 模型分类 | 随机选路径 | 1~2 条完整路径 | **必选** | L1→L2→L3 链 |
| 赛事来源 | 固定 `原创题目` | 1 | **固定** | AI 生成均标记原创 |
| 赛题年份 | 不选 | 0 | 跳过 | AI 生成无年份属性 |
| 行业领域 | 随机 0~2 个 | 0~2 | 可选 | 60% 概率选取 |
| 数据特征 | 随机 0~2 个 | 0~2 | 可选 | 50% 概率选取 |

### 4.2 层级路径随机算法

对于题目类型和模型分类这两个三级层级分类，需要从根到叶选出一条完整路径：

```
算法：RandomPathSelect(categoryId)

输入：分类 ID（5=题目类型，6=模型分类）
输出：一条标签路径 [L1, L2, L3?]

步骤：
1. 查询该分类下所有 status=1 的 L1 标签（parent_id=0）
2. 随机选取 1 个 L1 → selectedL1
3. 查询 selectedL1 下所有 L2 标签（parent_id=selectedL1.id）
4. 随机选取 1 个 L2 → selectedL2
5. 查询 selectedL2 下所有 L3 标签（parent_id=selectedL2.id）
6. 如果存在 L3：随机选取 1 个 L3 → 返回 [L1, L2, L3]
7. 如果不存在 L3（叶子 L2）：返回 [L1, L2]
```

**伪代码：**

```java
public List<Tag> selectRandomPath(Long categoryId) {
    List<Tag> path = new ArrayList<>();

    // 1. 选 L1
    List<Tag> l1Tags = tagMapper.selectByCategory(categoryId, 0); // parent_id=0
    Tag l1 = randomPick(l1Tags);
    path.add(l1);

    // 2. 选 L2
    List<Tag> l2Tags = tagMapper.selectByParent(l1.getId());
    if (l2Tags.isEmpty()) return path; // 扁平分类无子节点
    Tag l2 = randomPick(l2Tags);
    path.add(l2);

    // 3. 选 L3（如有）
    List<Tag> l3Tags = tagMapper.selectByParent(l2.getId());
    if (!l3Tags.isEmpty()) {
        Tag l3 = randomPick(l3Tags);
        path.add(l3);
    }

    return path;
}
```

### 4.3 用户指定标签的合并策略

用户可在请求中指定部分标签，其余由系统随机补全：

```
用户指定: { difficulty: "difficulty_hard", industry: ["ind_transportation"] }
系统补全: problem_type路径、model_category路径、data_feature

合并规则：
- 用户指定的标签 → 直接采用，不随机
- 用户未指定的必选分类 → 系统随机选取
- 用户未指定的可选分类 → 按概率决定是否选取
```

### 4.4 最终标签集合示例

一次完整的标签选取结果：

```json
{
  "selected_tags": {
    "difficulty": ["difficulty_hard"],
    "role": ["role_comprehensive"],
    "competition_source": ["comp_original"],
    "problem_type": ["ptype_operations_research", "ptype_logistics_opt", "ptype_vrp"],
    "model_category": [
      ["model_g_optimization", "model_math_programming", "model_ip"],
      ["model_g_optimization", "model_metaheuristic", "model_ga"]
    ],
    "industry": ["ind_transportation", "ind_logistics"],
    "data_feature": ["data_structured"]
  },
  "prompt_context": {
    "difficulty_text": "困难",
    "problem_type_path": "运筹优化 > 物流优化 > 车辆路径",
    "model_paths": [
      "优化模型 > 数学规划 > 整数规划",
      "优化模型 > 智能算法 > 遗传算法"
    ],
    "industry_text": "交通运输、物流供应链",
    "data_feature_text": "结构化数据"
  }
}
```

---

## 五、提示词工程设计

### 5.1 提示词架构

采用 **System + User** 双层提示词结构：

```
┌─────────────────────────────────────────────┐
│  System Prompt（固定，定义角色与规则）        │
│  - AI 身份：数学建模命题专家                  │
│  - 输出格式：严格 JSON                       │
│  - 质量要求：6 条硬性规则                     │
└─────────────────────────────────────────────┘
                    +
┌─────────────────────────────────────────────┐
│  User Prompt（动态，包含标签上下文）           │
│  - 难度等级                                  │
│  - 题目类型路径                               │
│  - 模型方法路径                               │
│  - 行业领域（如有）                           │
│  - 数据特征（如有）                           │
│  - 子问题数量要求                             │
└─────────────────────────────────────────────┘
```

### 5.2 System Prompt（固定）

```text
你是一位经验丰富的全国大学生数学建模竞赛命题专家，拥有十年以上国赛（CUMCM）和美赛（MCM/ICM）命题与评审经验。

你的任务是根据给定的标签约束条件，生成一道高质量的数学建模练习题目。

【输出格式要求】
你必须且只能输出一个合法的 JSON 对象，不要输出任何其他文字、解释或 Markdown 标记。JSON 结构如下：
{
  "title": "题目标题（简洁概括，15-30字）",
  "background": "背景描述（300-600字，描述真实可信的应用场景，包含必要的领域背景知识）",
  "questions": [
    {
      "number": 1,
      "content": "子问题描述（清晰、具体、可执行）",
      "points": "该小题考察的核心知识点与方法"
    }
  ],
  "data_description": "数据说明（描述题目提供或需要收集的数据类型、格式、规模）",
  "hints": "解题思路提示（100-200字，简述建议的建模方法与求解路径）"
}

【质量规则】
1. 背景必须基于真实、可信的应用场景，不得虚构不存在的机构或数据
2. 子问题必须有层次性，从基础建模到深入分析层层递进
3. 每个子问题必须是可独立作答的完整问题，不是模糊的讨论方向
4. 题目必须能体现指定的模型方法，但不要在题面中直接说"请使用XX模型"
5. 数据说明要具体，说明数据字段、量级和格式
6. 严格输出合法 JSON，不要添加任何注释或额外文字
```

### 5.3 User Prompt（动态模板）

```text
请根据以下条件生成一道数学建模练习题目：

【难度等级】{difficulty_text}
【题目类型】{problem_type_path}
【涉及模型方法】
{model_paths_formatted}
{industry_section}
{data_feature_section}

【子问题数量要求】{question_count_range}

请注意：
- 难度"{difficulty_text}"意味着{difficulty_description}
- 题目类型决定了问题的领域方向，请围绕"{problem_type_leaf}"方向命题
- 涉及的模型方法应在解题过程中自然使用，不要在题面中提示具体模型名称
```

### 5.4 动态区段构建规则

**难度描述映射：**

| 难度 | `difficulty_description` | `question_count_range` |
|:---:|--------------------------|:---:|
| 入门 | 零基础学生可独立完成，考察单一建模方法的基本应用 | 2 题 |
| 简单 | 需掌握基本建模方法，能完成简单的数据分析与模型求解 | 2~3 题 |
| 中等 | 需组合运用 2-3 种方法，涉及模型验证与结果分析 | 3 题 |
| 困难 | 接近正式赛题难度，需要创新性建模思路和多方法融合 | 3~4 题 |
| 地狱 | 高度开放性问题，需要深厚的数学功底和创新能力 | 4~5 题 |

**行业领域区段（有则拼接，无则省略）：**

```text
【行业领域】{industry_text}
（请将题目背景设定在{industry_text}相关场景中）
```

**数据特征区段（有则拼接，无则省略）：**

```text
【数据特征】{data_feature_text}
（题目涉及的数据应具有{data_feature_text}特点）
```

### 5.5 完整 Prompt 拼接示例

**System Prompt：**（见 5.2，固定不变）

**User Prompt 示例：**

```text
请根据以下条件生成一道数学建模练习题目：

【难度等级】困难
【题目类型】运筹优化 > 物流优化 > 车辆路径
【涉及模型方法】
- 优化模型 > 数学规划 > 整数规划
- 优化模型 > 智能算法 > 遗传算法

【行业领域】交通运输、物流供应链
（请将题目背景设定在交通运输、物流供应链相关场景中）

【数据特征】结构化数据
（题目涉及的数据应具有结构化数据特点）

【子问题数量要求】3~4题

请注意：
- 难度"困难"意味着接近正式赛题难度，需要创新性建模思路和多方法融合
- 题目类型决定了问题的领域方向，请围绕"车辆路径"方向命题
- 涉及的模型方法应在解题过程中自然使用，不要在题面中提示具体模型名称
```

---

## 六、API 接口设计

### 6.1 生成题目

```
POST /api/v1/problems/generate
```

**请求体：**

```json
{
  "overrides": {
    "difficulty": "difficulty_hard",
    "problem_type_l1": "ptype_operations_research",
    "model_category_l1": "model_g_optimization",
    "industry": ["ind_transportation"],
    "data_feature": []
  }
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|:---:|------|
| `overrides` | Object | 否 | 指定标签覆盖，未指定字段由系统随机 |
| `overrides.difficulty` | String | 否 | 难度等级 code |
| `overrides.problem_type_l1` | String | 否 | 题目类型 L1 的 code，L2/L3 仍随机 |
| `overrides.model_category_l1` | String | 否 | 模型分类 L1 的 code，L2/L3 仍随机 |
| `overrides.industry` | String[] | 否 | 行业领域 code 数组 |
| `overrides.data_feature` | String[] | 否 | 数据特征 code 数组 |

> 如果 `overrides` 为空或不传，则所有标签全部随机选取。

**成功响应 `200`：**

```json
{
  "code": 200,
  "message": "生成成功",
  "data": {
    "problem_id": 10001,
    "title": "城市冷链物流配送路径优化问题",
    "background": "随着生鲜电商的快速发展...",
    "questions": [
      {
        "number": 1,
        "content": "请建立冷链物流车辆配送的数学模型...",
        "points": "车辆路径问题建模、整数规划"
      },
      {
        "number": 2,
        "content": "考虑温控约束和时间窗限制...",
        "points": "约束优化、多目标规划"
      },
      {
        "number": 3,
        "content": "设计一种启发式算法...",
        "points": "遗传算法、算法设计"
      },
      {
        "number": 4,
        "content": "对你的模型和算法进行灵敏度分析...",
        "points": "灵敏度分析、鲁棒性"
      }
    ],
    "data_description": "题目提供某城市 50 个社区配送点的经纬度坐标...",
    "hints": "建议先建立基础的 VRP 模型...",
    "tags": {
      "difficulty": { "id": 1010004, "name": "困难", "color": "#F44336" },
      "role": { "id": 2010004, "name": "综合", "color": "#6A1B9A" },
      "competition_source": { "id": 3010015, "name": "原创题目", "color": "#FFB300" },
      "problem_type": [
        { "id": 5010004, "name": "运筹优化", "level": "L1" },
        { "id": 5020013, "name": "物流优化", "level": "L2" },
        { "id": 5030031, "name": "车辆路径", "level": "L3" }
      ],
      "model_category": [
        { "id": 6010001, "name": "优化模型", "level": "L1" },
        { "id": 6020001, "name": "数学规划", "level": "L2" },
        { "id": 6030003, "name": "整数规划", "level": "L3" },
        { "id": 6020002, "name": "智能算法", "level": "L2" },
        { "id": 6030007, "name": "遗传算法", "level": "L3" }
      ],
      "industry": [
        { "id": 7010001, "name": "交通运输", "color": "#E53935" }
      ],
      "data_feature": [
        { "id": 8010001, "name": "结构化数据", "color": "#1E88E5" }
      ]
    },
    "source_type": 0,
    "status": 0,
    "created_at": "2025-01-15T10:30:00"
  }
}
```

### 6.2 重新生成（保留标签）

```
POST /api/v1/problems/{problemId}/regenerate
```

使用原始题目的标签快照 `ai_tag_snapshot` 重新生成，替换内容。适用于对生成质量不满意时快速重试。

**成功响应：** 结构同 6.1，`problem_id` 不变，内容更新。

### 6.3 发布题目

```
PATCH /api/v1/problems/{problemId}/publish
```

将 AI 生成的草稿题目状态从 `0-草稿` 变为 `1-已发布`。

```json
{
  "code": 200,
  "message": "发布成功",
  "data": { "problem_id": 10001, "status": 1 }
}
```

---

## 七、核心服务架构

### 7.1 包结构

```
com.leetmodel.problem
├── controller
│   └── ProblemGenerateController.java      // API 入口
├── dto
│   ├── GenerateRequest.java                // 生成请求 DTO
│   ├── GenerateOverrides.java              // 用户指定标签
│   └── GenerateResult.java                 // 生成结果 DTO
├── service
│   ├── ProblemGenerateService.java         // 生成主流程编排
│   ├── TagSelector.java                    // 标签随机选取
│   ├── PromptBuilder.java                  // 提示词构建
│   └── AiResponseParser.java              // AI 响应解析
├── model
│   ├── SelectedTags.java                   // 选中的标签集合
│   ├── TagPath.java                        // 标签路径（L1→L2→L3）
│   └── AiGeneratedProblem.java             // AI 返回的题目结构
├── config
│   └── AiClientConfig.java                // 火山引擎配置
└── client
    └── VolcEngineAiClient.java             // 火山引擎 API 封装
```

### 7.2 核心类设计

#### SelectedTags — 选中的标签集合

```java
@Data
public class SelectedTags {
    private Tag difficulty;                    // 难度（1个）
    private Tag role;                          // 角色（固定：综合）
    private Tag competitionSource;             // 赛事来源（固定：原创题目）
    private List<TagPath> problemTypePaths;    // 题目类型路径（1条）
    private List<TagPath> modelCategoryPaths;  // 模型分类路径（1~2条）
    private List<Tag> industries;              // 行业领域（0~2个）
    private List<Tag> dataFeatures;            // 数据特征（0~2个）

    /** 获取所有叶子标签 ID，用于 problem_tag 关联 */
    public List<Long> getAllTagIds() {
        List<Long> ids = new ArrayList<>();
        ids.add(difficulty.getId());
        ids.add(role.getId());
        ids.add(competitionSource.getId());
        // 路径中所有层级都关联
        problemTypePaths.forEach(p -> ids.addAll(p.getAllIds()));
        modelCategoryPaths.forEach(p -> ids.addAll(p.getAllIds()));
        industries.forEach(t -> ids.add(t.getId()));
        dataFeatures.forEach(t -> ids.add(t.getId()));
        return ids;
    }
}
```

#### TagPath — 标签路径

```java
@Data
public class TagPath {
    private Tag l1;
    private Tag l2;      // 可能为 null（扁平分类）
    private Tag l3;      // 可能为 null（叶子 L2）

    /** 获取路径所有标签 ID */
    public List<Long> getAllIds() {
        List<Long> ids = new ArrayList<>();
        ids.add(l1.getId());
        if (l2 != null) ids.add(l2.getId());
        if (l3 != null) ids.add(l3.getId());
        return ids;
    }

    /** 生成面包屑文本："运筹优化 > 物流优化 > 车辆路径" */
    public String toBreadcrumb() {
        StringBuilder sb = new StringBuilder(l1.getName());
        if (l2 != null) sb.append(" > ").append(l2.getName());
        if (l3 != null) sb.append(" > ").append(l3.getName());
        return sb.toString();
    }

    /** 获取叶子标签名（用于 prompt 中的"请围绕 XX 方向命题"） */
    public String getLeafName() {
        if (l3 != null) return l3.getName();
        if (l2 != null) return l2.getName();
        return l1.getName();
    }
}
```

#### AiGeneratedProblem — AI 响应映射

```java
@Data
public class AiGeneratedProblem {
    private String title;
    private String background;
    private List<Question> questions;
    private String dataDescription;
    private String hints;

    @Data
    public static class Question {
        private Integer number;
        private String content;
        private String points;
    }
}
```

### 7.3 主流程编排 — ProblemGenerateService

```java
@Service
@RequiredArgsConstructor
public class ProblemGenerateService {

    private final TagSelector tagSelector;
    private final PromptBuilder promptBuilder;
    private final VolcEngineAiClient aiClient;
    private final AiResponseParser responseParser;
    private final ProblemMapper problemMapper;
    private final ProblemTagMapper problemTagMapper;
    private final TagMapper tagMapper;

    @Transactional
    public GenerateResult generate(GenerateRequest request) {

        // ========== 1. 标签选取 ==========
        SelectedTags selectedTags = tagSelector.select(request.getOverrides());

        // ========== 2. 构建提示词 ==========
        String systemPrompt = promptBuilder.buildSystemPrompt();
        String userPrompt = promptBuilder.buildUserPrompt(selectedTags);

        // ========== 3. 调用 AI ==========
        String aiResponse = aiClient.chat(systemPrompt, userPrompt);

        // ========== 4. 解析响应 ==========
        AiGeneratedProblem generated = responseParser.parse(aiResponse);

        // ========== 5. 构建完整题面 content ==========
        String content = buildFullContent(generated);

        // ========== 6. 存储题目 ==========
        Problem problem = new Problem();
        problem.setTitle(generated.getTitle());
        problem.setBackground(generated.getBackground());
        problem.setContent(content);
        problem.setQuestions(toJson(generated.getQuestions()));
        problem.setDataDescription(generated.getDataDescription());
        problem.setHints(generated.getHints());
        problem.setSourceType(0);  // AI 生成
        problem.setStatus(0);      // 草稿
        problem.setAiPrompt(userPrompt);
        problem.setAiModel(aiClient.getModelName());
        problem.setAiTagSnapshot(toJson(selectedTags.toSnapshot()));
        problemMapper.insert(problem);

        // ========== 7. 绑定标签 ==========
        List<Long> tagIds = selectedTags.getAllTagIds();
        for (Long tagId : tagIds) {
            ProblemTag pt = new ProblemTag();
            pt.setProblemId(problem.getId());
            pt.setTagId(tagId);
            problemTagMapper.insert(pt);
        }

        // ========== 8. 更新 usage_count ==========
        tagMapper.batchIncrementUsageCount(tagIds);

        // ========== 9. 构建返回 ==========
        return buildResult(problem, selectedTags, generated);
    }

    /** 将结构化子问题拼接为完整的 Markdown 题面 */
    private String buildFullContent(AiGeneratedProblem generated) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 背景\n\n").append(generated.getBackground()).append("\n\n");
        sb.append("## 问题\n\n");
        for (AiGeneratedProblem.Question q : generated.getQuestions()) {
            sb.append("**问题 ").append(q.getNumber()).append("：** ")
              .append(q.getContent()).append("\n\n");
        }
        if (generated.getDataDescription() != null) {
            sb.append("## 数据说明\n\n").append(generated.getDataDescription()).append("\n\n");
        }
        return sb.toString();
    }
}
```

### 7.4 TagSelector — 标签选取器

```java
@Component
@RequiredArgsConstructor
public class TagSelector {

    private final TagMapper tagMapper;
    private final ThreadLocalRandom random = ThreadLocalRandom.current();

    // 固定标签
    private static final String ROLE_COMPREHENSIVE = "role_comprehensive";
    private static final String COMP_ORIGINAL = "comp_original";

    public SelectedTags select(GenerateOverrides overrides) {
        SelectedTags result = new SelectedTags();

        // 1. 难度：用户指定 or 随机
        if (overrides != null && overrides.getDifficulty() != null) {
            result.setDifficulty(tagMapper.selectByCode(overrides.getDifficulty()));
        } else {
            result.setDifficulty(randomFromCategory(1L));
        }

        // 2. 角色：固定综合
        result.setRole(tagMapper.selectByCode(ROLE_COMPREHENSIVE));

        // 3. 赛事来源：固定原创
        result.setCompetitionSource(tagMapper.selectByCode(COMP_ORIGINAL));

        // 4. 题目类型路径
        List<TagPath> ptypePaths = new ArrayList<>();
        if (overrides != null && overrides.getProblemTypeL1() != null) {
            Tag l1 = tagMapper.selectByCode(overrides.getProblemTypeL1());
            ptypePaths.add(randomPathFromL1(l1));
        } else {
            ptypePaths.add(randomPath(5L));
        }
        result.setProblemTypePaths(ptypePaths);

        // 5. 模型分类路径（1~2 条）
        int modelPathCount = 1 + random.nextInt(2); // 1 或 2
        List<TagPath> modelPaths = new ArrayList<>();
        Set<Long> usedL1Ids = new HashSet<>();
        for (int i = 0; i < modelPathCount; i++) {
            TagPath path;
            if (i == 0 && overrides != null && overrides.getModelCategoryL1() != null) {
                Tag l1 = tagMapper.selectByCode(overrides.getModelCategoryL1());
                path = randomPathFromL1(l1);
            } else {
                path = randomPathExcluding(6L, usedL1Ids);
            }
            usedL1Ids.add(path.getL1().getId());
            modelPaths.add(path);
        }
        result.setModelCategoryPaths(modelPaths);

        // 6. 行业领域（60% 概率选取，选 1~2 个）
        if (overrides != null && overrides.getIndustry() != null && !overrides.getIndustry().isEmpty()) {
            result.setIndustries(overrides.getIndustry().stream()
                .map(tagMapper::selectByCode).collect(Collectors.toList()));
        } else if (random.nextDouble() < 0.6) {
            int count = 1 + random.nextInt(2);
            result.setIndustries(randomMultipleFromCategory(7L, count));
        } else {
            result.setIndustries(Collections.emptyList());
        }

        // 7. 数据特征（50% 概率选取，选 1~2 个）
        if (overrides != null && overrides.getDataFeature() != null && !overrides.getDataFeature().isEmpty()) {
            result.setDataFeatures(overrides.getDataFeature().stream()
                .map(tagMapper::selectByCode).collect(Collectors.toList()));
        } else if (random.nextDouble() < 0.5) {
            int count = 1 + random.nextInt(2);
            result.setDataFeatures(randomMultipleFromCategory(8L, count));
        } else {
            result.setDataFeatures(Collections.emptyList());
        }

        return result;
    }

    /** 从某分类下的 L1 标签中随机选一个 */
    private Tag randomFromCategory(Long categoryId) {
        List<Tag> tags = tagMapper.selectByCategoryAndParent(categoryId, 0L);
        return tags.get(random.nextInt(tags.size()));
    }

    /** 从某分类随机选一条完整路径 */
    private TagPath randomPath(Long categoryId) {
        Tag l1 = randomFromCategory(categoryId);
        return randomPathFromL1(l1);
    }

    /** 从指定 L1 向下随机选路径 */
    private TagPath randomPathFromL1(Tag l1) {
        TagPath path = new TagPath();
        path.setL1(l1);

        List<Tag> l2List = tagMapper.selectByParent(l1.getId());
        if (!l2List.isEmpty()) {
            Tag l2 = l2List.get(random.nextInt(l2List.size()));
            path.setL2(l2);

            List<Tag> l3List = tagMapper.selectByParent(l2.getId());
            if (!l3List.isEmpty()) {
                path.setL3(l3List.get(random.nextInt(l3List.size())));
            }
        }
        return path;
    }
}
```

### 7.5 PromptBuilder — 提示词构建器

```java
@Component
public class PromptBuilder {

    private static final Map<String, String> DIFFICULTY_DESC = Map.of(
        "difficulty_beginner", "零基础学生可独立完成，考察单一建模方法的基本应用",
        "difficulty_easy",     "需掌握基本建模方法，能完成简单的数据分析与模型求解",
        "difficulty_medium",   "需组合运用2-3种方法，涉及模型验证与结果分析",
        "difficulty_hard",     "接近正式赛题难度，需要创新性建模思路和多方法融合",
        "difficulty_hell",     "高度开放性问题，需要深厚的数学功底和创新能力"
    );

    private static final Map<String, String> QUESTION_COUNT = Map.of(
        "difficulty_beginner", "2题",
        "difficulty_easy",     "2~3题",
        "difficulty_medium",   "3题",
        "difficulty_hard",     "3~4题",
        "difficulty_hell",     "4~5题"
    );

    public String buildSystemPrompt() {
        return """
            你是一位经验丰富的全国大学生数学建模竞赛命题专家......
            """; // 见 5.2 完整内容
    }

    public String buildUserPrompt(SelectedTags tags) {
        StringBuilder sb = new StringBuilder();

        sb.append("请根据以下条件生成一道数学建模练习题目：\n\n");

        // 难度
        String diffCode = tags.getDifficulty().getCode();
        sb.append("【难度等级】").append(tags.getDifficulty().getName()).append("\n");

        // 题目类型路径
        sb.append("【题目类型】");
        tags.getProblemTypePaths().forEach(p -> sb.append(p.toBreadcrumb()));
        sb.append("\n");

        // 模型方法路径
        sb.append("【涉及模型方法】\n");
        tags.getModelCategoryPaths().forEach(p ->
            sb.append("- ").append(p.toBreadcrumb()).append("\n")
        );

        // 行业（可选）
        if (!tags.getIndustries().isEmpty()) {
            String industryText = tags.getIndustries().stream()
                .map(Tag::getName).collect(Collectors.joining("、"));
            sb.append("\n【行业领域】").append(industryText).append("\n");
            sb.append("（请将题目背景设定在").append(industryText).append("相关场景中）\n");
        }

        // 数据特征（可选）
        if (!tags.getDataFeatures().isEmpty()) {
            String dfText = tags.getDataFeatures().stream()
                .map(Tag::getName).collect(Collectors.joining("、"));
            sb.append("\n【数据特征】").append(dfText).append("\n");
            sb.append("（题目涉及的数据应具有").append(dfText).append("特点）\n");
        }

        // 子问题数量
        sb.append("\n【子问题数量要求】").append(QUESTION_COUNT.get(diffCode)).append("\n");

        // 补充说明
        sb.append("\n请注意：\n");
        sb.append("- 难度\"").append(tags.getDifficulty().getName())
          .append("\"意味着").append(DIFFICULTY_DESC.get(diffCode)).append("\n");

        String leafName = tags.getProblemTypePaths().get(0).getLeafName();
        sb.append("- 题目类型决定了问题的领域方向，请围绕\"")
          .append(leafName).append("\"方向命题\n");

        sb.append("- 涉及的模型方法应在解题过程中自然使用，不要在题面中提示具体模型名称\n");

        return sb.toString();
    }
}
```

### 7.6 AiResponseParser — 响应解析器

```java
@Component
public class AiResponseParser {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public AiGeneratedProblem parse(String aiResponse) {
        try {
            // 1. 提取 JSON（AI 可能在 JSON 前后添加文字）
            String json = extractJson(aiResponse);

            // 2. 反序列化
            AiGeneratedProblem problem = objectMapper.readValue(json, AiGeneratedProblem.class);

            // 3. 基础校验
            validate(problem);

            return problem;
        } catch (JsonProcessingException e) {
            throw new AiResponseParseException("AI 返回内容无法解析为合法 JSON", e);
        }
    }

    /** 从 AI 响应中提取 JSON 对象（处理可能的 ```json 包裹） */
    private String extractJson(String raw) {
        String trimmed = raw.trim();

        // 处理 ```json ... ``` 包裹
        if (trimmed.startsWith("```")) {
            int start = trimmed.indexOf('{');
            int end = trimmed.lastIndexOf('}');
            if (start >= 0 && end > start) {
                return trimmed.substring(start, end + 1);
            }
        }

        // 直接寻找第一个 { 和最后一个 }
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1);
        }

        return trimmed;
    }

    /** 校验必填字段 */
    private void validate(AiGeneratedProblem problem) {
        if (problem.getTitle() == null || problem.getTitle().isBlank()) {
            throw new AiResponseParseException("AI 生成题目缺少标题");
        }
        if (problem.getBackground() == null || problem.getBackground().isBlank()) {
            throw new AiResponseParseException("AI 生成题目缺少背景描述");
        }
        if (problem.getQuestions() == null || problem.getQuestions().isEmpty()) {
            throw new AiResponseParseException("AI 生成题目缺少子问题");
        }
    }
}
```

### 7.7 VolcEngineAiClient — 火山引擎 AI 客户端

```java
@Component
@RequiredArgsConstructor
public class VolcEngineAiClient {

    private final AiClientConfig config;
    private final RestTemplate restTemplate;

    public String chat(String systemPrompt, String userPrompt) {
        // 构建请求体（火山引擎 OpenAI 兼容格式）
        Map<String, Object> request = Map.of(
            "model", config.getModelName(),
            "messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)
            ),
            "temperature", 0.8,       // 适度创造性
            "max_tokens", 4096,
            "response_format", Map.of("type", "json_object")  // 强制 JSON
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(config.getApiKey());

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);

        ResponseEntity<Map> response = restTemplate.exchange(
            config.getEndpoint(),
            HttpMethod.POST,
            entity,
            Map.class
        );

        // 提取 content
        Map body = response.getBody();
        List<Map> choices = (List<Map>) body.get("choices");
        Map message = (Map) choices.get(0).get("message");
        return (String) message.get("content");
    }

    public String getModelName() {
        return config.getModelName();
    }
}
```

**配置类：**

```java
@Data
@Component
@ConfigurationProperties(prefix = "ai.volcengine")
public class AiClientConfig {
    private String endpoint;    // https://ark.cn-beijing.volces.com/api/v3/chat/completions
    private String apiKey;
    private String modelName;   // doubao-pro-256k / doubao-1.5-pro-256k 等
}
```

**application.yml：**

```yaml
ai:
  volcengine:
    endpoint: https://ark.cn-beijing.volces.com/api/v3/chat/completions
    api-key: ${VOLC_AI_API_KEY}
    model-name: doubao-pro-256k
```

---

## 八、AI 响应解析与存储流程

### 8.1 完整数据流

```
AI JSON 响应
     │
     ▼
┌─────────────────────────┐
│  AiResponseParser.parse │ ← 提取 JSON + 反序列化 + 校验
│  → AiGeneratedProblem   │
└────────────┬────────────┘
             │
             ▼
┌─────────────────────────┐
│  构建 content Markdown   │ ← 拼接背景 + 子问题为完整题面
└────────────┬────────────┘
             │
             ▼
┌─────────────────────────┐
│  创建 Problem 实体       │
│  ┌─────────────────┐    │
│  │ title            │    │
│  │ background       │    │
│  │ content (MD)     │    │ ← 前端渲染用完整 Markdown
│  │ questions (JSON) │    │ ← 结构化数据，API 返回用
│  │ data_description │    │
│  │ hints            │    │
│  │ source_type = 0  │    │
│  │ status = 0       │    │ ← 草稿状态
│  │ ai_prompt        │    │ ← 存储 user prompt
│  │ ai_model         │    │ ← 存储模型名称
│  │ ai_tag_snapshot   │    │ ← 存储标签快照 JSON
│  └─────────────────┘    │
└────────────┬────────────┘
             │
             ▼
┌─────────────────────────┐
│  INSERT problem          │ → 获得 problem.id
└────────────┬────────────┘
             │
             ▼
┌─────────────────────────┐
│  批量 INSERT problem_tag │ ← selectedTags.getAllTagIds()
│                          │   每个标签 ID 与 problem.id 关联
└────────────┬────────────┘
             │
             ▼
┌─────────────────────────┐
│  批量 UPDATE tag         │ ← usage_count + 1
│  SET usage_count = usage_count + 1
│  WHERE id IN (tagIds)    │
└──────────────────────────┘
```

### 8.2 标签关联策略

题目关联的标签包括**路径上所有层级**的标签，而非仅叶子节点：

```
路径：优化模型 > 数学规划 > 整数规划

关联的 tag_id：
- 06010001 (优化模型, L1)
- 06020001 (数学规划, L2)
- 06030003 (整数规划, L3)
```

**原因**：用户筛选时可能选择任意层级（选 L1"优化模型"应能找到该题目），所以每一级都要关联。

### 8.3 problem_tag 写入示例

一道完整生成的题目可能产生如下关联记录：

| problem_id | tag_id | 标签名 | 来源分类 |
|:---:|:---:|:---|:---|
| 10001 | 01010004 | 困难 | 难度等级 |
| 10001 | 02010004 | 综合 | 角色方向 |
| 10001 | 03010015 | 原创题目 | 赛事来源 |
| 10001 | 05010004 | 运筹优化 | 题目类型 L1 |
| 10001 | 05020013 | 物流优化 | 题目类型 L2 |
| 10001 | 05030031 | 车辆路径 | 题目类型 L3 |
| 10001 | 06010001 | 优化模型 | 模型分类 L1 |
| 10001 | 06020001 | 数学规划 | 模型分类 L2 |
| 10001 | 06030003 | 整数规划 | 模型分类 L3 |
| 10001 | 06020002 | 智能算法 | 模型分类 L2 |
| 10001 | 06030007 | 遗传算法 | 模型分类 L3 |
| 10001 | 07010001 | 交通运输 | 行业领域 |
| 10001 | 08010001 | 结构化数据 | 数据特征 |

共 **13 条**关联记录，**13 个标签** 的 `usage_count` 各 +1。

---

## 九、异常处理与降级策略

### 9.1 异常分类与处理

| 异常场景 | 异常类型 | 处理策略 |
|---------|---------|---------|
| 火山引擎 API 超时 | `SocketTimeoutException` | 重试 1 次，仍失败则返回错误提示 |
| 火山引擎 API 限流 | HTTP 429 | 等待 2 秒后重试 1 次 |
| AI 返回非法 JSON | `JsonProcessingException` | 尝试提取修复，失败则重新调用 1 次 |
| AI 返回内容缺失必填字段 | `AiResponseParseException` | 重新调用 1 次（相同 prompt） |
| 标签数据不存在 | `TagNotFoundException` | 返回 400 错误，提示标签 code 无效 |
| 数据库写入失败 | `DataAccessException` | 事务回滚，返回 500 错误 |

### 9.2 重试机制

```java
@Retryable(
    value = {AiServiceException.class},
    maxAttempts = 2,
    backoff = @Backoff(delay = 2000)
)
public String chat(String systemPrompt, String userPrompt) {
    // ... AI 调用逻辑
}
```

### 9.3 超时配置

```yaml
ai:
  volcengine:
    connect-timeout: 10000   # 连接超时 10s
    read-timeout: 60000      # 读取超时 60s（大模型生成需要时间）
```

---

## 十、快速开发路线图

### 10.1 分步实施计划

```
Phase 1: 基础通路（Day 1-2）
├── ① 创建 problem 表
├── ② 实现 VolcEngineAiClient（硬编码 prompt 测试连通性）
├── ③ 实现 AiResponseParser（JSON 提取 + 反序列化）
└── ④ 手动拼 prompt 调用 → 验证端到端通路

Phase 2: 标签选取（Day 3）
├── ⑤ 实现 TagSelector（随机选取 + 路径构建）
├── ⑥ 实现 PromptBuilder（动态 prompt 拼接）
└── ⑦ 单元测试：验证标签选取分布合理性

Phase 3: API 集成（Day 4）
├── ⑧ 实现 ProblemGenerateService（主流程编排）
├── ⑨ 实现 ProblemGenerateController（REST API）
├── ⑩ 实现 problem_tag 关联写入 + usage_count 更新
└── ⑪ Postman 测试完整流程

Phase 4: 前端联调（Day 5）
├── ⑫ 前端生成页面（按钮 + 标签选择 + 结果展示）
├── ⑬ 前端题目预览页面（Markdown 渲染）
└── ⑭ 发布功能（草稿 → 已发布）
```

### 10.2 Demo 阶段简化项

| 简化项 | 说明 | 后续完善方向 |
|--------|------|-------------|
| 不做批量生成 | 单次生成 1 题 | 后续加消息队列做批量任务 |
| 不做人工审核流 | 生成即存为草稿，手动发布 | 后续加审核工作流 |
| 不做 prompt 版本管理 | prompt 写在代码中 | 后续抽到数据库/配置中心 |
| 不做生成质量评分 | 不自动判断质量 | 后续加 AI 自评 + 人工评分 |
| 不做标签智能推荐 | 纯随机 | 后续加基于历史数据的智能组合 |

### 10.3 验证检查清单

```
□ 调用 POST /api/v1/problems/generate（不传 overrides）
  → 返回完整题目 + 随机标签
  → problem 表有新记录，status=0
  → problem_tag 表有关联记录
  → 对应 tag 的 usage_count 已 +1

□ 调用 POST /api/v1/problems/generate（指定 difficulty=difficulty_hell）
  → 返回的题目难度为"地狱"
  → 子问题数量为 4~5 题

□ 连续生成 5 题
  → 标签组合各不相同
  → 题目内容各不相同

□ 调用 PATCH /api/v1/problems/{id}/publish
  → status 变为 1

□ AI 返回异常格式
  → 自动重试 1 次
  → 最终失败返回友好错误信息
```

---

> **本文档定义了 AI 生成题目功能的完整设计**，覆盖标签随机选取策略、提示词工程、数据模型、API 接口、核心服务架构、响应解析与异常处理。按照 Phase 1-4 的路线图，预计 **5 个工作日**可完成可演示的 Demo。