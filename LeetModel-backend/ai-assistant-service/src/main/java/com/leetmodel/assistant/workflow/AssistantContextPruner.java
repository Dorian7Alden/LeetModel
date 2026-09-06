package com.leetmodel.assistant.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leetmodel.assistant.entity.AssistantMessage;
import com.leetmodel.common.ai.model.AiContentPart;
import com.leetmodel.common.ai.model.AiContentType;
import com.leetmodel.common.ai.model.AiMessage;
import com.leetmodel.common.ai.model.AiRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;

/**
 * AI 客服多轮上下文智能修剪与工具事实折叠组件。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AssistantContextPruner {

    public static final int DEFAULT_HISTORY_TOKEN_BUDGET = 3000;
    private static final int CHARS_PER_TOKEN = 2;

    private final ObjectMapper objectMapper;

    /**
     * 将历史已完成消息修剪并组装为符合 Token 预算的 AiMessage 列表。
     *
     * @param history            会话最近消息（时间正序）
     * @param currentUserMessage 当前轮次的用户消息
     * @param tokenBudget        历史 Token 预算上限
     * @return 经过工具事实折叠与滑动窗口裁剪的 AiMessage 列表
     */
    public List<AiMessage> pruneAndFoldHistory(List<AssistantMessage> history,
                                               AssistantMessage currentUserMessage,
                                               int tokenBudget) {
        if (history == null || history.isEmpty()) {
            return List.of();
        }

        // 1. 先对所有历史消息进行内容精简与工具事实折叠（当前提问除外）
        List<AiMessage> candidateMessages = new ArrayList<>();
        for (AssistantMessage item : history) {
            boolean isCurrent = Objects.equals(item.getId(), currentUserMessage.getId());
            AiRole role = "ASSISTANT".equalsIgnoreCase(item.getRole()) ? AiRole.ASSISTANT : AiRole.USER;
            String text = item.getContent() == null ? "" : item.getContent();

            if (!isCurrent) {
                if (role == AiRole.ASSISTANT && item.getToolContextJson() != null && !item.getToolContextJson().isBlank()) {
                    String foldedFact = foldToolContext(item.getToolContextJson());
                    if (!foldedFact.isBlank()) {
                        text = text.isBlank() ? foldedFact : text + "\n" + foldedFact;
                    }
                }
                text = collapseBulkyBlocks(text);
            }

            candidateMessages.add(new AiMessage(role, List.of(new AiContentPart(AiContentType.TEXT, text, null))));
        }

        // 2. 逆向计算 Token 开销并在超出预算时按轮次截断
        int maxChars = Math.max(20, tokenBudget * CHARS_PER_TOKEN);
        return applySlidingWindow(candidateMessages, maxChars);
    }

    /**
     * 将工具原始 JSON 事实折叠为高密度的紧凑事实标记。
     */
    public String foldToolContext(String toolContextJson) {
        if (toolContextJson == null || toolContextJson.isBlank()) {
            return "";
        }
        try {
            JsonNode root = objectMapper.readTree(toolContextJson);
            if (root.isArray()) {
                StringJoiner joiner = new StringJoiner("; ");
                for (JsonNode node : root) {
                    if (node.has("name") && node.has("result")) {
                        String toolName = node.path("name").asText();
                        JsonNode result = node.path("result");
                        String summarized = summarizeToolResult(toolName, result);
                        if (!summarized.isBlank()) {
                            joiner.add(summarized);
                        }
                    } else if (node.has("code") && node.has("title")) {
                        joiner.add(formatProblemItem(node));
                    }
                }
                if (joiner.length() > 0) {
                    return "[历史工具事实: " + joiner + "]";
                }
            } else if (root.isObject() && root.has("items")) {
                String summarized = summarizeProblemItems(root.path("items"));
                if (!summarized.isBlank()) {
                    return "[历史工具事实: 推荐题目 -> " + summarized + "]";
                }
            }
        } catch (Exception e) {
            log.debug("折叠工具上下文失败，保持静默: {}", e.getMessage());
        }
        return "";
    }

    private String summarizeToolResult(String toolName, JsonNode result) {
        if ("search_problem".equals(toolName) || "recommend_problems".equals(toolName)) {
            JsonNode items = result.path("items");
            String summarized = summarizeProblemItems(items);
            return summarized.isBlank() ? toolName + " 未找到匹配题目" : toolName + " -> " + summarized;
        } else if ("explain_modeling_knowledge".equals(toolName)) {
            return "explain_modeling_knowledge -> 已讲解该知识点";
        }
        return toolName + " 已执行";
    }

    private String summarizeProblemItems(JsonNode itemsNode) {
        if (itemsNode == null || !itemsNode.isArray() || itemsNode.isEmpty()) {
            return "";
        }
        StringJoiner sj = new StringJoiner(", ");
        for (int i = 0; i < itemsNode.size() && i < 5; i++) {
            sj.add(formatProblemItem(itemsNode.get(i)));
        }
        return sj.toString();
    }

    private String formatProblemItem(JsonNode item) {
        int code = item.path("code").asInt();
        String title = item.path("title").asText("未知");
        int year = item.path("year").asInt();
        int difficulty = item.path("difficulty").asInt();
        StringBuilder sb = new StringBuilder();
        sb.append("P").append(code).append("《").append(title).append("》");
        if (year > 0 || difficulty > 0) {
            sb.append("(");
            if (year > 0) sb.append(year).append("年");
            if (difficulty > 0) {
                if (year > 0) sb.append(", ");
                sb.append("难度").append(difficulty);
            }
            sb.append(")");
        }
        return sb.toString();
    }

    private String collapseBulkyBlocks(String text) {
        if (text == null) return "";
        if (text.contains("系统只读题目候选（只能依据这些数据推荐）：")) {
            int idx = text.indexOf("系统只读题目候选（只能依据这些数据推荐）：");
            return text.substring(0, idx).trim() + "\n[历史系统题目候选已折叠]";
        }
        if (text.contains("BEGIN_UNTRUSTED_TOOL_RESULT")) {
            return text.replaceAll("BEGIN_UNTRUSTED_TOOL_RESULT[\\s\\S]*?END_UNTRUSTED_TOOL_RESULT", "[工具原始数据已折叠]");
        }
        return text;
    }

    private List<AiMessage> applySlidingWindow(List<AiMessage> messages, int maxChars) {
        if (messages.size() <= 2) {
            return messages;
        }

        int currentChars = 0;
        int keepStartIndex = 0;

        for (int i = messages.size() - 1; i >= 0; i--) {
            AiMessage msg = messages.get(i);
            int len = messageLength(msg);
            if (currentChars + len > maxChars && i < messages.size() - 2) {
                keepStartIndex = i + 1;
                break;
            }
            currentChars += len;
        }

        if (keepStartIndex == 0) {
            return messages;
        }

        while (keepStartIndex < messages.size() && messages.get(keepStartIndex).role() != AiRole.USER) {
            keepStartIndex++;
        }

        return messages.subList(keepStartIndex, messages.size());
    }

    private int messageLength(AiMessage message) {
        if (message == null || message.content() == null) return 0;
        return message.content().stream()
                .filter(p -> p.type() == AiContentType.TEXT && p.text() != null)
                .mapToInt(p -> p.text().length())
                .sum();
    }
}
