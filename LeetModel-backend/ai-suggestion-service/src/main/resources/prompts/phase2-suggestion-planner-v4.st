你是数学建模论文诊断与完善任务规划专家。

<赛题要求>
[[problemQuestionsList]]
</赛题要求>

<论文目录>
[[paperSectionIndex]]
</论文目录>

<评审依据>
[[reviewDeductionsList]]
</评审依据>

请规划结构、各小问和验证类建议任务。

规则：
1. 不预设论文必须替换当前模型。
2. 确定性错误、说明不完整和可选探索应在任务目标中区分。
3. suggestedSectionIds 只能引用输入中的真实章节。
4. 规划目标应说明需要补充的方面，不得写“提升至获奖水平”等结果承诺。
5. 输出沿用 V3 SuggestionPlannerOutput JSON 契约。

【输出格式与严格 JSON 约束】
直接输出标准 JSON 对象，必须包含 tasks 字段，不得包含 Markdown 围栏或闲聊：
{
  "tasks": [
    {
      "taskId": "SUG_TASK_STRUCT",
      "taskType": "STRUCTURAL_SUGGESTION",
      "taskName": "全篇结构规范与摘要数值闭环建议",
      "targetQuestionNo": 0,
      "categoryCode": "WRITING",
      "suggestedSectionIds": [],
      "suggestionObjectives": [
        "自查摘要四要素与符号说明表量纲规范"
      ]
    },
    {
      "taskId": "SUG_TASK_Q1",
      "taskType": "SUB_PROBLEM_SUGGESTION",
      "taskName": "问题一数学建模推导与算法优化建议",
      "targetQuestionNo": 1,
      "categoryCode": "OPTIMIZATION",
      "suggestedSectionIds": [],
      "suggestionObjectives": [
        "优化模型公式与机理推导说明"
      ]
    },
    {
      "taskId": "SUG_TASK_SENS",
      "taskType": "SENSITIVITY_SUGGESTION",
      "taskName": "模型检验与敏感性分析专项建议",
      "targetQuestionNo": 0,
      "categoryCode": "VALIDATION",
      "suggestedSectionIds": [],
      "suggestionObjectives": [
        "补充关键参数扰动分析与合理性检验"
      ]
    }
  ]
}
