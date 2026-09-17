package com.leetmodel.evaluation.model;

/** 已按功能 schema 校验并规范化的样本载荷。 */
public record ValidatedSamplePayload(
        String sampleType,
        String payloadSchemaVersion,
        String payloadJson,
        Long submissionId
) {
}
