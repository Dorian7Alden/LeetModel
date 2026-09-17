package com.leetmodel.assistant.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leetmodel.assistant.entity.AssistantMessage;
import com.leetmodel.common.ai.model.AiMessage;
import com.leetmodel.common.ai.model.AiRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AssistantContextPrunerTest {

    private AssistantContextPruner pruner;

    @BeforeEach
    void setUp() {
        pruner = new AssistantContextPruner(new ObjectMapper());
    }

    @Test
    void foldToolContextShrinksVerboseProblemJsonIntoCompactFact() {
        String rawJson = """
                [
                  {
                    "name": "recommend_problems",
                    "version": "1.0.0",
                    "result": {
                      "items": [
                        {
                          "code": 1001,
                          "title": "电力系统短期负荷预测模型",
                          "contestName": "美赛",
                          "year": 2024,
                          "difficulty": 2,
                          "overview": "这是一段非常冗长的背景描述，包含成百上千字的历史数据介绍与数学建模比赛要求..."
                        },
                        {
                          "code": 1003,
                          "title": "共享单车调度优化",
                          "contestName": "国赛",
                          "year": 2023,
                          "difficulty": 1,
                          "overview": "这是另一段冗长的题目概览说明..."
                        }
                      ]
                    }
                  }
                ]
                """;

        String folded = pruner.foldToolContext(rawJson);

        assertThat(folded).contains("[历史工具事实: recommend_problems ->");
        assertThat(folded).contains("P1001《电力系统短期负荷预测模型》(2024年, 难度2)");
        assertThat(folded).contains("P1003《共享单车调度优化》(2023年, 难度1)");
        assertThat(folded).doesNotContain("非常冗长的背景描述");
        assertThat(folded.length()).isLessThan(rawJson.length() / 2);
    }

    @Test
    void pruneAndFoldHistoryCollapsesOldToolResultsAndRespectsBudget() {
        AssistantMessage m1 = createMessage(1L, "USER", "请推荐一些简单题目");
        AssistantMessage m2 = createMessage(2L, "ASSISTANT", "我为你找到了两道题目：");
        m2.setToolContextJson("""
                [{"name":"search_problem","result":{"items":[{"code":1001,"title":"题A","year":2024,"difficulty":1}]}}]
                """);
        AssistantMessage m3 = createMessage(3L, "USER", "第二道题目是什么？");

        List<AiMessage> pruned = pruner.pruneAndFoldHistory(List.of(m1, m2, m3), m3, 1000);

        assertThat(pruned).hasSize(3);
        AiMessage assistantMsg = pruned.get(1);
        assertThat(assistantMsg.content().get(0).text()).contains("P1001《题A》(2024年, 难度1)");
    }

    @Test
    void slidingWindowDiscardsOlderTurnsWhenExceedingBudget() {
        AssistantMessage m1 = createMessage(1L, "USER", "这是第一轮很长的问题".repeat(20));
        AssistantMessage m2 = createMessage(2L, "ASSISTANT", "这是第一轮很长的回答".repeat(20));
        AssistantMessage m3 = createMessage(3L, "USER", "这是第二轮的问题");
        AssistantMessage m4 = createMessage(4L, "ASSISTANT", "这是第二轮的回答");
        AssistantMessage m5 = createMessage(5L, "USER", "这是第三轮的问题");

        // 设置极小预算，强制淘汰第一轮
        List<AiMessage> pruned = pruner.pruneAndFoldHistory(List.of(m1, m2, m3, m4, m5), m5, 60);

        // 保留的起始消息应为第 2 轮的 USER 消息，淘汰第 1 轮完整对话
        assertThat(pruned.get(0).role()).isEqualTo(AiRole.USER);
        assertThat(pruned.get(0).content().get(0).text()).isEqualTo("这是第二轮的问题");
        assertThat(pruned).hasSize(3);
    }

    private AssistantMessage createMessage(Long id, String role, String content) {
        AssistantMessage msg = new AssistantMessage();
        msg.setId(id);
        msg.setRole(role);
        msg.setContent(content);
        msg.setStatus("COMPLETED");
        return msg;
    }
}
