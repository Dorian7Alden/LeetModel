package com.leetmodel.assistant.rag.workflow;

/** 已组装且带索引版本的工作流知识上下文。 */
public record RagWorkflowContext(String text, String ragIndexVersion) {

    public boolean present() {
        return text != null && !text.isBlank();
    }

    public static RagWorkflowContext empty() {
        return new RagWorkflowContext(null, null);
    }
}
