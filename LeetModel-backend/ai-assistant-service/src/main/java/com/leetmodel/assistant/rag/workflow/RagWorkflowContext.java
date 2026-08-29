package com.leetmodel.assistant.rag.workflow;

import java.util.List;

/** 已组装且带索引版本的工作流知识上下文。 */
public record RagWorkflowContext(String text, String ragIndexVersion,
                                 int retrievedChunkCount, List<String> sourcePaths) {

    public RagWorkflowContext(String text, String ragIndexVersion) {
        this(text, ragIndexVersion, 0, List.of());
    }

    public RagWorkflowContext {
        sourcePaths = sourcePaths == null ? List.of() : List.copyOf(sourcePaths);
    }

    public boolean present() {
        return text != null && !text.isBlank();
    }

    public static RagWorkflowContext empty() {
        return new RagWorkflowContext(null, null, 0, List.of());
    }
}
