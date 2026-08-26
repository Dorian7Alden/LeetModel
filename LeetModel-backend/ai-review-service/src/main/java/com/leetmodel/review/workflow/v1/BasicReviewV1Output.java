package com.leetmodel.review.workflow.v1;

import java.math.BigDecimal;
import java.util.List;

public record BasicReviewV1Output(
        BigDecimal score,
        String summary,
        Dimensions dimensions,
        List<String> strengths,
        List<String> weaknesses,
        List<String> suggestions
) {
    public record Dimensions(
            Dimension assumptionRationality,
            Dimension modelCreativity,
            Dimension resultCorrectness,
            Dimension expressionClarity
    ) {
    }

    public record Dimension(BigDecimal score, String comment) {
    }
}
