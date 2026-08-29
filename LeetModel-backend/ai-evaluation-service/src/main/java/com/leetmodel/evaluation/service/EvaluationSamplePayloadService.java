package com.leetmodel.evaluation.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leetmodel.common.api.dto.EvaluationSamplePayloadDTO;
import com.leetmodel.evaluation.model.ValidatedSamplePayload;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Iterator;
import java.util.Set;

/** 按 feature 和 schema 约束评价样本，避免无结构 JSON 进入评价库。 */
@Service
@RequiredArgsConstructor
public class EvaluationSamplePayloadService {

    public static final String REVIEW_SAMPLE_TYPE = "SUBMISSION_REFERENCE";
    public static final String REVIEW_SCHEMA = "REVIEW_SUBMISSION_V1";
    public static final String ASSISTANT_SAMPLE_TYPE = "QUESTION";
    public static final String ASSISTANT_SCHEMA = "ASSISTANT_QUESTION_V1";

    private static final Set<String> REVIEW_FIELDS = Set.of("submissionId");
    private static final Set<String> ASSISTANT_FIELDS = Set.of("question", "tags", "expectedPoints",
            "expectedSources", "formatRules");
    private static final Set<String> ASSISTANT_FORMAT_RULES = Set.of("ANSWER_NON_BLANK",
            "ANSWER_MAX_2000", "NO_MARKDOWN_CODE_FENCE", "REQUIRES_SOURCE_MARKER");

    private final ObjectMapper objectMapper;

    public ValidatedSamplePayload validate(String featureCode, EvaluationSamplePayloadDTO input) {
        if (input == null) throw new IllegalArgumentException("样本载荷不能为空");
        JsonNode payload = parseObject(input.getPayloadJson());
        return switch (required(featureCode, "功能编码")) {
            case "REVIEW" -> validateReview(input, payload);
            case "ASSISTANT" -> validateAssistant(input, payload);
            default -> throw new IllegalArgumentException("不支持的评价功能: " + featureCode);
        };
    }

    private ValidatedSamplePayload validateReview(EvaluationSamplePayloadDTO input, JsonNode payload) {
        requireIdentity(input, REVIEW_SAMPLE_TYPE, REVIEW_SCHEMA);
        requireOnlyFields(payload, REVIEW_FIELDS);
        JsonNode submissionId = payload.get("submissionId");
        if (submissionId == null || !submissionId.canConvertToLong() || submissionId.longValue() <= 0) {
            throw new IllegalArgumentException("REVIEW 样本 submissionId 必须为正整数");
        }
        return normalized(input, payload, submissionId.longValue());
    }

    private ValidatedSamplePayload validateAssistant(EvaluationSamplePayloadDTO input, JsonNode payload) {
        requireIdentity(input, ASSISTANT_SAMPLE_TYPE, ASSISTANT_SCHEMA);
        requireOnlyFields(payload, ASSISTANT_FIELDS);
        String question = text(payload.get("question"));
        if (question == null || question.length() > 4000) {
            throw new IllegalArgumentException("ASSISTANT 样本 question 长度必须为1至4000个字符");
        }
        validateStringArray(payload.get("tags"), "tags", 20, 100);
        validateStringArray(payload.get("expectedPoints"), "expectedPoints", 20, 500);
        validateStringArray(payload.get("expectedSources"), "expectedSources", 20, 500);
        validateFormatRules(payload.get("formatRules"));
        return normalized(input, payload, null);
    }

    private void validateFormatRules(JsonNode value) {
        validateStringArray(value, "formatRules", 10, 100);
        if (value == null || value.isNull()) return;
        for (JsonNode item : value) {
            if (!ASSISTANT_FORMAT_RULES.contains(item.textValue())) {
                throw new IllegalArgumentException("formatRules 包含不支持的规则: " + item.textValue());
            }
        }
    }

    private JsonNode parseObject(String payloadJson) {
        try {
            JsonNode payload = objectMapper.readTree(required(payloadJson, "样本载荷"));
            if (!payload.isObject()) throw new IllegalArgumentException("样本载荷必须是 JSON 对象");
            return payload;
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("样本载荷不是有效 JSON", exception);
        }
    }

    private void requireIdentity(EvaluationSamplePayloadDTO input, String sampleType, String schema) {
        if (!sampleType.equals(input.getSampleType()) || !schema.equals(input.getPayloadSchemaVersion())) {
            throw new IllegalArgumentException("样本类型与 schema 不匹配");
        }
    }

    private void requireOnlyFields(JsonNode payload, Set<String> allowed) {
        Iterator<String> fields = payload.fieldNames();
        while (fields.hasNext()) {
            String field = fields.next();
            if (!allowed.contains(field)) throw new IllegalArgumentException("样本载荷包含未知字段: " + field);
        }
    }

    private void validateStringArray(JsonNode value, String field, int maxItems, int maxLength) {
        if (value == null || value.isNull()) return;
        if (!value.isArray() || value.size() > maxItems) {
            throw new IllegalArgumentException(field + " 必须为不超过" + maxItems + "项的字符串数组");
        }
        for (JsonNode item : value) {
            String text = text(item);
            if (text == null || text.length() > maxLength) {
                throw new IllegalArgumentException(field + " 包含无效字符串");
            }
        }
    }

    private String text(JsonNode value) {
        if (value == null || !value.isTextual() || value.textValue().isBlank()) return null;
        return value.textValue().trim();
    }

    private ValidatedSamplePayload normalized(EvaluationSamplePayloadDTO input, JsonNode payload,
                                               Long submissionId) {
        try {
            return new ValidatedSamplePayload(input.getSampleType(), input.getPayloadSchemaVersion(),
                    objectMapper.writeValueAsString(payload), submissionId);
        } catch (Exception exception) {
            throw new IllegalArgumentException("样本载荷无法规范化", exception);
        }
    }

    private String required(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + "不能为空");
        return value.trim();
    }
}
