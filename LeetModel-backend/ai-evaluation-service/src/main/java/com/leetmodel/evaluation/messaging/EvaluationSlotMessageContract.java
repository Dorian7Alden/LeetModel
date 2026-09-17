package com.leetmodel.evaluation.messaging;

public final class EvaluationSlotMessageContract {
    public static final String TOPIC = "evaluation-task-v1";
    public static final String EVENT_TYPE = "EVALUATION_SLOT_READY";
    public static final String CONSUMER_GROUP = "cg-ai-evaluation-task-v1";

    private EvaluationSlotMessageContract() {
    }

    public static String idempotencyKey(Long runAttemptId, Integer attemptNo, long wakeupBucket) {
        return "evaluation:run:" + runAttemptId + ":attempt:" + attemptNo
                + ":wakeup:" + wakeupBucket;
    }
}
