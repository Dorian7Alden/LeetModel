package com.leetmodel.common.api.audit;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** 首期 P0 操作目录及其允许的来源、操作者和审计摘要字段。 */
public final class OperationAuditCatalog {

    private static final Set<String> ADMIN_ACTORS = Set.of("ADMIN", "SERVICE");
    private static final Set<String> USER_ACTORS = Set.of("USER", "ADMIN");
    private static final Set<String> AUTOMATION_ACTORS = Set.of("ADMIN", "SERVICE", "SYSTEM");
    private static final Set<String> MESSAGE_OWNER_SERVICES = Set.of(
            "submission-service",
            "ai-review-service",
            "ai-suggestion-service",
            "ai-evaluation-service",
            "ranking-service",
            "audit-service"
    );
    private static final Map<String, Spec> SPECS = specs();

    private OperationAuditCatalog() {
    }

    /** 按稳定编码查询操作；未知编码 fail-fast。 */
    public static Spec require(String operationCode) {
        Spec spec = SPECS.get(operationCode);
        if (spec == null) throw new OperationAuditContractException("unsupported operationCode");
        return spec;
    }

    /** 只读目录，用于 audit-service 投影与契约测试。 */
    public static Map<String, Spec> all() {
        return SPECS;
    }

    private static Map<String, Spec> specs() {
        Map<String, Spec> result = new LinkedHashMap<>();
        add(result, "AUTH.LOGIN_SUCCESS", "IDENTITY_SECURITY", "MEDIUM", "user-service", "USER",
                false, 0, Set.of("USER"), fields("authMethod", "sessionClass"));
        add(result, "AUTH.LOGIN_FAILED", "IDENTITY_SECURITY", "MEDIUM", "user-service", "USER",
                false, 0, Set.of("USER"), fields("authMethod", "failureClass"));
        add(result, "USER.PASSWORD_CHANGE", "IDENTITY_SECURITY", "HIGH", "user-service", "USER",
                false, 0, USER_ACTORS, fields("credentialVersion"));
        add(result, "USER.STATUS_CHANGE", "USER_RBAC", "HIGH", "user-service", "USER",
                false, 0, ADMIN_ACTORS, fields("status", "reasonCode"));
        add(result, "USER.ROLE_CHANGE", "USER_RBAC", "HIGH", "user-service", "USER",
                false, 0, ADMIN_ACTORS, fields("roleCount", "roleSetHash"));
        add(result, "ROLE.PERMISSION_CHANGE", "USER_RBAC", "HIGH", "user-service", "ROLE",
                false, 0, ADMIN_ACTORS, fields("permissionCount", "permissionSetHash"));
        add(result, "PROBLEM.CREATE", "CONTENT_GOVERNANCE", "MEDIUM", "problem-service", "PROBLEM",
                false, 0, ADMIN_ACTORS, fields("visibility", "tagCount", "attachmentCount", "contentVersion"));
        add(result, "PROBLEM.UPDATE", "CONTENT_GOVERNANCE", "MEDIUM", "problem-service", "PROBLEM",
                false, 0, ADMIN_ACTORS, fields("visibility", "tagCount", "attachmentCount", "contentVersion"));
        add(result, "PROBLEM.DELETE", "CONTENT_GOVERNANCE", "HIGH", "problem-service", "PROBLEM",
                false, 0, ADMIN_ACTORS, fields("visibility", "contentVersion"));
        add(result, "PROBLEM.ATTACHMENT_DELETE", "CONTENT_GOVERNANCE", "HIGH", "problem-service", "ATTACHMENT",
                false, 0, ADMIN_ACTORS, fields("attachmentKind", "attachmentVersion"));
        add(result, "CONTEST.UPDATE", "CONTENT_GOVERNANCE", "MEDIUM", "problem-service", "CONTEST",
                false, 0, ADMIN_ACTORS, fields("visibility", "scheduleVersion", "problemCount"));
        add(result, "SUBMISSION.FINALIZE", "SUBMISSION_GOVERNANCE", "HIGH", "submission-service", "SUBMISSION",
                false, 0, USER_ACTORS, fields("submissionVersion", "finalized"));
        add(result, "AI_QUEUE.CANCEL", "AI_GOVERNANCE", "MEDIUM", "ai-gateway-service", "AI_CALL_TASK",
                false, 0, Set.of("USER", "ADMIN", "SERVICE"), fields("taskState", "cancelRequested"));
        add(result, "EVALUATION.PAUSE", "AI_GOVERNANCE", "HIGH", "ai-evaluation-service", "EVALUATION_TASK",
                false, 0, ADMIN_ACTORS, fields("taskState", "pauseReasonCode"));
        add(result, "EVALUATION.RESUME", "AI_GOVERNANCE", "HIGH", "ai-evaluation-service", "EVALUATION_TASK",
                false, 0, ADMIN_ACTORS, fields("taskState", "resumeReasonCode"));
        add(result, "EVALUATION.CANCEL", "AI_GOVERNANCE", "HIGH", "ai-evaluation-service", "EVALUATION_TASK",
                false, 0, ADMIN_ACTORS, fields("taskState", "cancelReasonCode"));
        add(result, "EVALUATION.RETRY", "AI_GOVERNANCE", "HIGH", "ai-evaluation-service", "EVALUATION_TASK",
                false, 0, ADMIN_ACTORS, fields("taskState", "retryReasonCode", "retryCount"));
        add(result, "ASSISTANT_CONFIG.ACTIVATE", "AI_VERSION_GOVERNANCE", "HIGH", "ai-assistant-service", "ASSISTANT_CONFIG",
                false, 0, ADMIN_ACTORS, fields("fromVersion", "toVersion", "revision"));
        add(result, "ASSISTANT_CONFIG.ROLLBACK", "AI_VERSION_GOVERNANCE", "HIGH", "ai-assistant-service", "ASSISTANT_CONFIG",
                false, 0, ADMIN_ACTORS, fields("fromVersion", "toVersion", "revision"));
        add(result, "WEIGHT_SCHEME.DEACTIVATE", "AI_VERSION_GOVERNANCE", "HIGH", "ai-evaluation-service", "WEIGHT_SCHEME",
                false, 0, ADMIN_ACTORS, fields("schemeVersion", "active"));
        add(result, "CONSUMER.PAUSE", "MESSAGING_GOVERNANCE", "HIGH", MESSAGE_OWNER_SERVICES, "MESSAGE_CONSUMER",
                true, 300, ADMIN_ACTORS, fields("topic", "consumerGroup", "pauseReasonCode"));
        add(result, "CONSUMER.RESUME", "MESSAGING_GOVERNANCE", "HIGH", MESSAGE_OWNER_SERVICES, "MESSAGE_CONSUMER",
                true, 300, ADMIN_ACTORS, fields("topic", "consumerGroup", "resumeReasonCode"));
        add(result, "OUTBOX.REPLAY", "MESSAGING_GOVERNANCE", "HIGH", MESSAGE_OWNER_SERVICES, "MESSAGE_OUTBOX",
                false, 0, ADMIN_ACTORS, fields("replayCount", "replayReasonCode", "eventHash"));
        add(result, "DLQ.REPLAY", "MESSAGING_GOVERNANCE", "HIGH", MESSAGE_OWNER_SERVICES, "MESSAGE_DLQ",
                true, 300, ADMIN_ACTORS, fields("replayCount", "replayReasonCode", "eventHash"));
        add(result, "RANKING.REBUILD", "DERIVED_DATA_GOVERNANCE", "MEDIUM", "ranking-service", "RANKING_SCOPE",
                false, 0, AUTOMATION_ACTORS, fields("scopeType", "affectedCount", "rebuildVersion"));
        add(result, "AUDIT.SEARCH_EXPORT", "AUDIT_GOVERNANCE", "HIGH", "audit-service", "AUDIT_EXPORT",
                true, 1800, ADMIN_ACTORS, fields("filterSchemaVersion", "resultCount", "exportFormat"));
        add(result, "AUDIT.RETENTION_CHANGE", "AUDIT_GOVERNANCE", "HIGH", "audit-service", "AUDIT_RETENTION_POLICY",
                false, 0, ADMIN_ACTORS, fields("fromPolicyVersion", "toPolicyVersion"));
        return Map.copyOf(result);
    }

    private static void add(Map<String, Spec> specs, String code, String category, String risk,
                            String service, String target, boolean external, long deadlineSeconds,
                            Set<String> actorTypes, Set<String> fields) {
        add(specs, code, category, risk, Set.of(service), target, external, deadlineSeconds,
                actorTypes, fields);
    }

    private static void add(Map<String, Spec> specs, String code, String category, String risk,
                            Set<String> services, String target, boolean external, long deadlineSeconds,
                            Set<String> actorTypes, Set<String> fields) {
        Spec previous = specs.put(code, new Spec(code, category, risk, services, target, external,
                deadlineSeconds, actorTypes, fields));
        if (previous != null) throw new IllegalStateException("duplicate audit operationCode: " + code);
    }

    private static Set<String> fields(String... names) {
        return Set.of(names);
    }

    /** 单个稳定编码的静态约束。 */
    public record Spec(
            String operationCode,
            String category,
            String riskLevel,
            Set<String> sourceServices,
            String targetType,
            boolean externalSideEffect,
            long completionDeadlineSeconds,
            Set<String> allowedActorTypes,
            Set<String> summaryFields
    ) {
        public Spec {
            sourceServices = Set.copyOf(sourceServices);
            allowedActorTypes = Set.copyOf(allowedActorTypes);
            summaryFields = Set.copyOf(summaryFields);
            if (externalSideEffect != (completionDeadlineSeconds > 0)) {
                throw new IllegalArgumentException("only external operations require a completion deadline");
            }
        }
    }
}
