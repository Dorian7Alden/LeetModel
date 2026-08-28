package com.leetmodel.assistant.rag.index;

import com.leetmodel.assistant.rag.config.RagProperties;
import com.leetmodel.assistant.rag.embedding.CommonAiEmbeddingModel;
import com.leetmodel.common.ai.client.AiClient;
import com.leetmodel.common.ai.model.AiCallContext;
import com.leetmodel.common.ai.model.AiCallPriority;
import com.leetmodel.common.ai.model.AiFeatureCode;
import com.leetmodel.common.ai.model.AiOperationCode;
import dev.langchain4j.model.embedding.EmbeddingModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Instant;
import java.util.UUID;

/** 索引任务专用的统一 Embedding 适配器。 */
@Configuration(proxyBeanMethods = false)
public class RagEmbeddingConfiguration {

    @Bean("ragIndexEmbeddingModel")
    EmbeddingModel ragIndexEmbeddingModel(AiClient aiClient, RagProperties properties) {
        return new CommonAiEmbeddingModel(aiClient, "RAG_V1", properties.getEmbeddingDimension(), segments -> {
            String taskId = "rag-index:" + UUID.randomUUID();
            return new AiCallContext("ai-assistant-service", AiFeatureCode.RAG,
                    AiOperationCode.INDEX_DOCUMENTS, taskId, null, null, "MODEL_CFG_RAG_V1",
                    null, AiCallPriority.P4, taskId, Instant.now().plus(properties.getRequestTimeout()));
        });
    }

    @Bean("ragQueryEmbeddingModel")
    EmbeddingModel ragQueryEmbeddingModel(AiClient aiClient, RagProperties properties) {
        return new CommonAiEmbeddingModel(aiClient, "RAG_V1", properties.getEmbeddingDimension(), segments -> {
            String taskId = "rag-query:" + UUID.randomUUID();
            return new AiCallContext("ai-assistant-service", AiFeatureCode.RAG,
                    AiOperationCode.RETRIEVE_CONTEXT, taskId, null, null, "MODEL_CFG_RAG_V1",
                    null, AiCallPriority.P0, taskId, Instant.now().plus(properties.getRequestTimeout()));
        });
    }
}
