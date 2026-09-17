package com.leetmodel.review.workflow;

import java.math.BigDecimal;

public record ReviewWorkflowResult(BigDecimal score, String resultJson, String modelName,
                                   String aiCallId, Long parseArtifactId) {
    public ReviewWorkflowResult(BigDecimal score, String resultJson, String modelName, String aiCallId) {
        this(score, resultJson, modelName, aiCallId, null);
    }
}
