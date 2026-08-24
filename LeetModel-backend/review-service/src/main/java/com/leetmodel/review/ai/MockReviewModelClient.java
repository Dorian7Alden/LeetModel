package com.leetmodel.review.ai;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
@Component
@ConditionalOnProperty(prefix = "review.ai", name = "mode", havingValue = "mock", matchIfMissing = true)
public class MockReviewModelClient implements ReviewModelClient {
    public String review(String paperText) {
        return "{\"totalScore\":75.0,\"summary\":\"基础评审已完成\",\"dimensions\":{\"model\":75,\"writing\":75}}";
    }
}
