package com.leetmodel.common.api.audit;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** 操作审计载荷的版本、目录、状态和数据最小化校验。 */
public final class OperationAuditContract {

    private static final int MAX_REASON_LENGTH = 300;
    private static final int MAX_SUMMARY_VALUE_LENGTH = 256;
    private static final int MAX_SUMMARY_TOTAL_LENGTH = 2_048;
    private static final Pattern EVENT_ID_PATTERN = Pattern.compile(
            "(?:[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12})"
                    + "|(?:[0-9A-HJKMNP-TV-Z]{26})"
    );
    private static final Pattern STABLE_ID_PATTERN = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]*");
    private static final Pattern ROLE_PATTERN = Pattern.compile("[A-Z][A-Z0-9_]{0,63}");
    private static final Pattern FAILURE_CODE_PATTERN = Pattern.compile("[A-Z][A-Z0-9_]{0,99}");
    private static final Pattern HASH_PATTERN = Pattern.compile("[0-9a-fA-F]{64}");
    private static final Pattern CONTROL_CHARACTER = Pattern.compile("[\\p{Cntrl}&&[^\\t]]");
    private static final Pattern SECRET_ASSIGNMENT = Pattern.compile(
            "(?i)(?:password|passwd|token|secret|authorization|api[-_]?key|prompt|answer|payload|paper|正文|论文|回答|密钥)\\s*[:=]"
    );
    private static final Pattern BEARER_CREDENTIAL = Pattern.compile("(?i)\\bbearer\\s+[A-Za-z0-9._~+/=-]{8,}");

    private OperationAuditContract() {
    }

    /** 校验公共载荷；任何未知版本、操作或不安全组合均 fail-fast。 */
    public static void validate(OperationAuditPayloadV1 payload) {
        if (payload == null) throw invalid("payload is required");
        if (payload.auditSchemaVersion() != OperationAuditPayloadV1.VERSION) {
            throw invalid("unsupported auditSchemaVersion");
        }
        requireEventId(payload.auditEventId(), "auditEventId");
        requireStableText(payload.operationId(), "operationId", 100);
        if (payload.occurredAt() == null) throw invalid("occurredAt is required");

        OperationAuditCatalog.Spec spec = OperationAuditCatalog.require(payload.operationCode());
        requireEqual(spec.category(), payload.category(), "category");
        requireEqual(spec.riskLevel(), payload.riskLevel(), "riskLevel");
        requireEqual(spec.targetType(), payload.targetType(), "targetType");
        requireStableText(payload.sourceService(), "sourceService", 100);
        if (!spec.sourceServices().contains(payload.sourceService())) {
            throw invalid("sourceService is not allowed for operationCode");
        }
        requireStableText(payload.serviceVersion(), "serviceVersion", 100);

        validateState(payload.phase(), payload.outcome(), spec.externalSideEffect());
        validateReasonAndFailure(payload);
        validateActor(payload, spec.allowedActorTypes());
        requireStableText(payload.targetId(), "targetId", 100);
        optionalStableText(payload.targetVersion(), "targetVersion", 100);
        validateSummary("beforeSummary", payload.beforeSummary(), spec.summaryFields());
        validateSummary("afterSummary", payload.afterSummary(), spec.summaryFields());

        requireStableText(payload.traceId(), "traceId", 100);
        optionalStableText(payload.swTraceId(), "swTraceId", 100);
        optionalStableText(payload.requestId(), "requestId", 100);
        optionalStableText(payload.domainTaskId(), "domainTaskId", 100);
        optionalStableText(payload.relatedEventId(), "relatedEventId", 100);
        optionalHash(payload.clientIpHash(), "clientIpHash");
        optionalHash(payload.userAgentHash(), "userAgentHash");
    }

    private static void validateState(String phase, String outcome, boolean externalSideEffect) {
        boolean pending = "REQUESTED".equals(phase) && "PENDING".equals(outcome);
        boolean terminal = "COMPLETED".equals(phase)
                && Set.of("SUCCEEDED", "FAILED", "REJECTED").contains(outcome);
        if (!pending && !terminal) throw invalid("unsupported phase/outcome combination");
        if (pending && !externalSideEffect) {
            throw invalid("REQUESTED/PENDING is only allowed for external side effects");
        }
    }

    private static void validateReasonAndFailure(OperationAuditPayloadV1 payload) {
        optionalSafeText(payload.reason(), "reason", MAX_REASON_LENGTH);
        if ("HIGH".equals(payload.riskLevel()) && isBlank(payload.reason())) {
            throw invalid("reason is required for HIGH risk operations");
        }
        boolean failed = "FAILED".equals(payload.outcome()) || "REJECTED".equals(payload.outcome());
        if (failed) {
            requireText(payload.failureCode(), "failureCode", 100);
            if (!FAILURE_CODE_PATTERN.matcher(payload.failureCode()).matches()) {
                throw invalid("failureCode must be a stable uppercase code");
            }
        } else if (payload.failureCode() != null) {
            throw invalid("failureCode is only allowed for FAILED or REJECTED outcomes");
        }
    }

    private static void validateActor(OperationAuditPayloadV1 payload, Set<String> allowedActorTypes) {
        requireStableText(payload.actorType(), "actorType", 16);
        if (!allowedActorTypes.contains(payload.actorType())) {
            throw invalid("actorType is not allowed for operationCode");
        }
        requireStableText(payload.actorId(), "actorId", 100);
        List<String> roles = payload.actorRolesSnapshot();
        if (roles.size() > 16) throw invalid("actorRolesSnapshot has too many values");
        if (("SERVICE".equals(payload.actorType()) || "SYSTEM".equals(payload.actorType())) && !roles.isEmpty()) {
            throw invalid("service and system actors cannot carry role snapshots");
        }
        Set<String> uniqueRoles = new HashSet<>();
        for (String role : roles) {
            if (role == null || !ROLE_PATTERN.matcher(role).matches()) {
                throw invalid("actorRolesSnapshot contains an invalid role");
            }
            if (!uniqueRoles.add(role)) throw invalid("actorRolesSnapshot contains duplicate roles");
        }
    }

    private static void validateSummary(String field, Map<String, String> summary, Set<String> allowedFields) {
        if (summary.size() > allowedFields.size()) throw invalid(field + " has too many fields");
        int totalLength = 0;
        for (Map.Entry<String, String> entry : summary.entrySet()) {
            if (!allowedFields.contains(entry.getKey())) throw invalid(field + " contains an undeclared field");
            String value = entry.getValue();
            requireSafeText(value, field + "." + entry.getKey(), MAX_SUMMARY_VALUE_LENGTH);
            if (value.startsWith("{") || value.startsWith("[")) {
                throw invalid(field + " cannot contain an entity snapshot");
            }
            totalLength += entry.getKey().length() + value.length();
        }
        if (totalLength > MAX_SUMMARY_TOTAL_LENGTH) throw invalid(field + " is too large");
    }

    private static void requireEventId(String value, String field) {
        requireText(value, field, 36);
        if (!EVENT_ID_PATTERN.matcher(value).matches()) throw invalid(field + " must be UUID or ULID");
    }

    private static void requireStableText(String value, String field, int maxLength) {
        requireText(value, field, maxLength);
        if (!STABLE_ID_PATTERN.matcher(value).matches()) {
            throw invalid(field + " contains unsupported characters");
        }
    }

    private static void optionalStableText(String value, String field, int maxLength) {
        if (value == null) return;
        requireStableText(value, field, maxLength);
    }

    private static void optionalHash(String value, String field) {
        if (value == null) return;
        if (!HASH_PATTERN.matcher(value).matches()) throw invalid(field + " must be a SHA-256 hex digest");
    }

    private static void optionalSafeText(String value, String field, int maxLength) {
        if (value == null) return;
        requireSafeText(value, field, maxLength);
    }

    private static void requireSafeText(String value, String field, int maxLength) {
        requireText(value, field, maxLength);
        if (CONTROL_CHARACTER.matcher(value).find()
                || SECRET_ASSIGNMENT.matcher(value).find()
                || BEARER_CREDENTIAL.matcher(value).find()
                || value.contains("-----BEGIN")) {
            throw invalid(field + " contains unsafe content");
        }
    }

    private static void requireText(String value, String field, int maxLength) {
        if (isBlank(value)) throw invalid(field + " is required");
        if (value.length() > maxLength) throw invalid(field + " is too long");
    }

    private static void requireEqual(String expected, String actual, String field) {
        if (!expected.equals(actual)) throw invalid(field + " does not match operation catalog");
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static OperationAuditContractException invalid(String message) {
        return new OperationAuditContractException(message);
    }
}
