package com.leetmodel.assistant.rag.retrieval;

/** 向量 Store 的可分类故障。 */
public class RagStoreException extends RuntimeException {

    private final boolean timeout;

    public RagStoreException(String message, boolean timeout, Throwable cause) {
        super(message, cause);
        this.timeout = timeout;
    }

    public boolean isTimeout() {
        return timeout;
    }
}
