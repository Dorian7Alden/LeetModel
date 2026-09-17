package com.leetmodel.assistant.rag.index;

import java.util.Map;
import java.util.Set;

/** 增量索引读取 manifest 和复制未变化片段的端口。 */
public interface RagIncrementalStore {

    Map<String, RagManifestDocument> readManifest(String readAlias);

    int copyUnchanged(String readAlias, String targetIndex, Set<String> documentIds, RagVersionSet versions);
}
