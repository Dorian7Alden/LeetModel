package com.leetmodel.assistant.rag.index;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** 通过 assistant.rag.index-command=INCREMENTAL 显式触发增量索引。 */
@Component
@ConditionalOnProperty(prefix = "assistant.rag", name = "index-command", havingValue = "INCREMENTAL")
public class RagIncrementalIndexCommandRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(RagIncrementalIndexCommandRunner.class);
    private final RagIncrementalIndexer indexer;

    public RagIncrementalIndexCommandRunner(RagIncrementalIndexer indexer) {
        this.indexer = indexer;
    }

    @Override
    public void run(ApplicationArguments args) {
        RagIncrementalIndexSummary summary = indexer.update();
        log.info("rag-index documents={} chunks={} failures={} version={}", summary.documentCount(),
                summary.chunkCount(), summary.failureCount(), summary.ragIndexVersion());
    }
}
