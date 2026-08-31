package com.leetmodel.assistant.tool;

import com.leetmodel.common.ai.model.AiChatResponse;

/**
 * 工具返回给外层模型和审计表的两个受控视图。
 *
 * @param modelResultJson 回传外层模型的白名单 JSON
 * @param auditSnapshotJson 保存到业务审计表的最小事实快照
 * @param terminalResponse 终止型工具的最终可见回答；普通工具为 null
 */
public record AssistantToolOutput(
        String modelResultJson,
        String auditSnapshotJson,
        AiChatResponse terminalResponse) {

    /** 创建普通数据工具结果。 */
    public static AssistantToolOutput data(String modelResultJson, String auditSnapshotJson) {
        return new AssistantToolOutput(modelResultJson, auditSnapshotJson, null);
    }
}
