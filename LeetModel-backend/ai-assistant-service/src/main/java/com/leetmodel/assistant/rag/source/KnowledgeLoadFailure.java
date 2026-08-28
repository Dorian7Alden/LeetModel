package com.leetmodel.assistant.rag.source;

/** 单个知识源加载失败的安全摘要，不包含正文和绝对路径。 */
public record KnowledgeLoadFailure(String relativePath, String errorType, String message) {
}
