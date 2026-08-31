package com.leetmodel.review.messaging;

/** 正式评审完成事件的稳定契约。 */
public final class ReviewCompletedMessageContract {
    public static final String TOPIC = "review-event-v1";
    public static final String EVENT_TYPE = "REVIEW_COMPLETED";
    public static final String CONSUMER_GROUP = "cg-ranking-review-v1";

    private ReviewCompletedMessageContract() {
    }

    public static String idempotencyKey(Long reviewTaskId) {
        return "review-completed:" + reviewTaskId;
    }
}
