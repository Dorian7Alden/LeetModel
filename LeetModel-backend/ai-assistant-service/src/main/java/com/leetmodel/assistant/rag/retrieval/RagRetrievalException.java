package com.leetmodel.assistant.rag.retrieval;

/** 检索链路对上层暴露的安全故障分类。 */
public class RagRetrievalException extends RuntimeException {

    private final Type type;

    public RagRetrievalException(Type type, Throwable cause) {
        super(type.name(), cause);
        this.type = type;
    }

    public Type getType() {
        return type;
    }

    public enum Type {
        EMBEDDING,
        ELASTICSEARCH,
        TIMEOUT,
        DIMENSION
    }
}
