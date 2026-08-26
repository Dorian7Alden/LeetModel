package com.leetmodel.review.workflow;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class ReviewWorkflowRegistry {
    private final Map<String, ReviewWorkflow> workflows;

    public ReviewWorkflowRegistry(java.util.List<ReviewWorkflow> workflows) {
        this.workflows = workflows.stream().collect(Collectors.toUnmodifiableMap(ReviewWorkflow::versionCode, Function.identity()));
    }

    public ReviewWorkflow required(String versionCode) {
        ReviewWorkflow workflow = workflows.get(versionCode);
        if (workflow == null) throw new IllegalArgumentException("未知评审版本: " + versionCode);
        return workflow;
    }
}
