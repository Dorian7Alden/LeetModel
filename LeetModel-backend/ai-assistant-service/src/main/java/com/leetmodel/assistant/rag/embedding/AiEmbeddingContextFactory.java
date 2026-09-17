package com.leetmodel.assistant.rag.embedding;

import com.leetmodel.common.ai.model.AiCallContext;
import dev.langchain4j.data.segment.TextSegment;

import java.util.List;

/** 为一次 LangChain4j Embedding 批次创建可审计调用上下文。 */
@FunctionalInterface
public interface AiEmbeddingContextFactory {
    AiCallContext create(List<TextSegment> segments);
}
