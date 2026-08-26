package com.leetmodel.review.workflow;

import java.math.BigDecimal;

public record ReviewWorkflowResult(BigDecimal score, String resultJson, String modelName, String aiCallId) {
}
