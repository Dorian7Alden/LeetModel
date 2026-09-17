package com.leetmodel.assistant.rag.index;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** 通过 assistant.rag.index-command=FULL 显式触发全量索引。 */
@Component
@ConditionalOnProperty(prefix = "assistant.rag", name = "index-command", havingValue = "FULL")
public class RagIndexCommandRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(RagIndexCommandRunner.class);
    private final RagFullIndexer indexer;

    public RagIndexCommandRunner(RagFullIndexer indexer) {
        this.indexer = indexer;
    }

    @Override
    public void run(ApplicationArguments args) {
        RagFullIndexSummary summary = indexer.rebuild();
        log.info("rag-index documents={} chunks={} failures={} version={}", summary.documentCount(),
                summary.chunkCount(), summary.failureCount(), summary.ragIndexVersion());
    }
}
