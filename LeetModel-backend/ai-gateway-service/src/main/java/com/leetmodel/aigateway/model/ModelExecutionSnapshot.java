package com.leetmodel.aigateway.model;

import com.leetmodel.common.ai.model.AiModality;
import com.leetmodel.common.ai.model.AiProvider;
import com.leetmodel.common.ai.model.AiResponseFormat;

/** 入队时锁定的模型执行事实；派发不再读取可变路由别名。 */
public record ModelExecutionSnapshot(
        String modelExecutionConfigVersion,
        String callType,
        String logicalModel,
        AiProvider provider,
        String model,
        AiModality modality,
        Integer maxTokens,
        Double temperature,
        AiResponseFormat responseFormat,
        Boolean thinkingEnabled,
        boolean tools,
        Integer embeddingDimension,
        Integer maxBatchSize,
        Integer maxInputChars,
        Integer maxTotalChars,
        String promptVersion,
        String workflowVersion
) {}
