package com.leetmodel.submission.messaging;

/** 最终提交变化事件的稳定契约。 */
public final class FinalSubmissionMessageContract {
    public static final String TOPIC = "submission-event-v1";
    public static final String EVENT_TYPE = "FINAL_SUBMISSION_CHANGED";
    public static final String CONSUMER_GROUP = "cg-ranking-submission-v1";

    private FinalSubmissionMessageContract() {
    }

    public static String idempotencyKey(Long teamId, Long submissionId) {
        return "final-submission:" + teamId + ":" + submissionId;
    }
}
