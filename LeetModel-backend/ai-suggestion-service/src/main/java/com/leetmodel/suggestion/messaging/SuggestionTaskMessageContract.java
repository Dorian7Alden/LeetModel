package com.leetmodel.suggestion.messaging;

public final class SuggestionTaskMessageContract {
    public static final String TOPIC = "suggestion-task-v1";
    public static final String EVENT_TYPE = "SUGGESTION_TASK_READY";
    public static final String CONSUMER_GROUP = "cg-ai-suggestion-task-v1";

    private SuggestionTaskMessageContract() {
    }

    public static String idempotencyKey(Long taskId, int attemptNo, long wakeupBucket) {
        return "suggestion:" + taskId + ":attempt:" + attemptNo + ":wakeup:" + wakeupBucket;
    }
}
