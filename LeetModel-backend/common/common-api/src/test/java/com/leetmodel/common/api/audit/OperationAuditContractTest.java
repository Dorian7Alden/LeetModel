package com.leetmodel.common.api.audit;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OperationAuditContractTest {

    @Test
    void shouldExposeClosedImmutableP0Catalog() {
        assertThat(OperationAuditCatalog.all()).hasSize(27);
        assertThat(OperationAuditCatalog.all().keySet()).contains(
                "AUTH.LOGIN_FAILED",
                "ROLE.PERMISSION_CHANGE",
                "SUBMISSION.FINALIZE",
                "ASSISTANT_CONFIG.ROLLBACK",
                "CONSUMER.PAUSE",
                "DLQ.REPLAY",
                "AUDIT.RETENTION_CHANGE"
        );
        assertThat(OperationAuditCatalog.require("CONSUMER.PAUSE").sourceServices())
                .containsExactlyInAnyOrder(
                        "submission-service", "ai-review-service", "ai-suggestion-service",
                        "ai-evaluation-service", "ranking-service", "audit-service"
                );
        assertThatThrownBy(() -> OperationAuditCatalog.all().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> OperationAuditCatalog.require("CONTROLLER.METHOD"))
                .isInstanceOf(OperationAuditContractException.class)
                .hasMessageContaining("unsupported operationCode");
    }

    @Test
    void shouldAcceptCatalogBoundTerminalAndExternalPendingEvents() {
        OperationAuditPayloadV1 terminal = builder("USER.ROLE_CHANGE").build();
        OperationAuditPayloadV1 pending = builder("CONSUMER.PAUSE")
                .phase("REQUESTED")
                .outcome("PENDING")
                .build();

        OperationAuditContract.validate(terminal);
        OperationAuditContract.validate(pending);

        assertThat(OperationAuditCatalog.require("CONSUMER.PAUSE").completionDeadlineSeconds())
                .isEqualTo(300);
    }

    @Test
    void shouldRejectUnknownVersionOperationAndCatalogMismatch() {
        assertInvalid(builder("USER.ROLE_CHANGE").schemaVersion(2).build(), "auditSchemaVersion");
        assertInvalid(builder("USER.ROLE_CHANGE").operationCode("USER.UNKNOWN").build(), "operationCode");
        assertInvalid(builder("USER.ROLE_CHANGE").category("CONTENT_GOVERNANCE").build(), "category");
        assertInvalid(builder("USER.ROLE_CHANGE").riskLevel("MEDIUM").build(), "riskLevel");
        assertInvalid(builder("USER.ROLE_CHANGE").sourceService("admin-service").build(), "sourceService");
        assertInvalid(builder("USER.ROLE_CHANGE").targetType("ENTITY").build(), "targetType");
        assertInvalid(builder("USER.ROLE_CHANGE").actorType("SYSTEM").build(), "actorType");
    }

    @Test
    void shouldRejectInvalidPhaseOutcomeAndFailureCombinations() {
        assertInvalid(builder("USER.ROLE_CHANGE").phase("REQUESTED").outcome("PENDING").build(),
                "external side effects");
        assertInvalid(builder("CONSUMER.PAUSE").phase("REQUESTED").outcome("SUCCEEDED").build(),
                "phase/outcome");
        assertInvalid(builder("USER.ROLE_CHANGE").outcome("FAILED").failureCode(null).build(),
                "failureCode");
        assertInvalid(builder("USER.ROLE_CHANGE").failureCode("SHOULD_NOT_EXIST").build(),
                "failureCode");
        assertInvalid(builder("USER.ROLE_CHANGE").reason(null).build(), "reason");
    }

    @Test
    void shouldRejectUndeclaredSnapshotAndSensitiveContent() {
        assertInvalid(builder("USER.ROLE_CHANGE")
                .beforeSummary(Map.of("email", "somebody@example.test"))
                .build(), "undeclared field");
        assertInvalid(builder("USER.ROLE_CHANGE")
                .afterSummary(Map.of("roleCount", "{\"roleCount\":2}"))
                .build(), "entity snapshot");
        assertInvalid(builder("USER.ROLE_CHANGE")
                .reason("token=do-not-archive")
                .build(), "unsafe content");
        assertInvalid(builder("USER.ROLE_CHANGE")
                .afterSummary(Map.of("roleCount", "x".repeat(257)))
                .build(), "too long");
    }

    @Test
    void shouldRejectInvalidActorAndSourceDigests() {
        assertInvalid(builder("USER.ROLE_CHANGE").actorType("ADMIN")
                .actorRoles(List.of("ROLE_ADMIN", "ROLE_ADMIN")).build(),
                "duplicate roles");
        assertInvalid(builder("RANKING.REBUILD").actorType("SYSTEM").actorRoles(List.of("ROLE_ADMIN")).build(),
                "cannot carry role snapshots");
        assertInvalid(builder("USER.ROLE_CHANGE").clientIpHash("192.0.2.10").build(), "SHA-256");
    }

    @Test
    void shouldDefensivelyCopyRoleAndSummaryCollections() {
        List<String> roles = new ArrayList<>(List.of("ROLE_ADMIN"));
        Map<String, String> before = new LinkedHashMap<>(Map.of("roleCount", "1"));
        OperationAuditPayloadV1 payload = builder("USER.ROLE_CHANGE").actorType("ADMIN")
                .actorRoles(roles)
                .beforeSummary(before)
                .build();

        roles.add("ROLE_AUDITOR");
        before.put("roleSetHash", "changed");

        assertThat(payload.actorRolesSnapshot()).containsExactly("ROLE_ADMIN");
        assertThat(payload.beforeSummary()).containsOnlyKeys("roleCount");
        assertThatThrownBy(() -> payload.afterSummary().put("roleCount", "3"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private void assertInvalid(OperationAuditPayloadV1 payload, String message) {
        assertThatThrownBy(() -> OperationAuditContract.validate(payload))
                .isInstanceOf(OperationAuditContractException.class)
                .hasMessageContaining(message);
    }

    private PayloadBuilder builder(String operationCode) {
        return new PayloadBuilder(operationCode);
    }

    private static final class PayloadBuilder {
        private int schemaVersion = OperationAuditPayloadV1.VERSION;
        private String operationCode;
        private String category;
        private String riskLevel;
        private String sourceService;
        private String targetType;
        private String phase = "COMPLETED";
        private String outcome = "SUCCEEDED";
        private String reason;
        private String failureCode;
        private String actorType;
        private List<String> actorRoles;
        private Map<String, String> beforeSummary;
        private Map<String, String> afterSummary;
        private String clientIpHash = "a".repeat(64);

        private PayloadBuilder(String operationCode) {
            OperationAuditCatalog.Spec spec = OperationAuditCatalog.require(operationCode);
            this.operationCode = operationCode;
            this.category = spec.category();
            this.riskLevel = spec.riskLevel();
            this.sourceService = spec.sourceServices().iterator().next();
            this.targetType = spec.targetType();
            this.actorType = spec.allowedActorTypes().iterator().next();
            this.actorRoles = "USER".equals(actorType) || "ADMIN".equals(actorType)
                    ? List.of("ROLE_ADMIN") : List.of();
            this.reason = "HIGH".equals(riskLevel) ? "approved-change" : null;
            String field = spec.summaryFields().iterator().next();
            this.beforeSummary = Map.of(field, "before");
            this.afterSummary = Map.of(field, "after");
        }

        private PayloadBuilder schemaVersion(int value) { schemaVersion = value; return this; }
        private PayloadBuilder operationCode(String value) { operationCode = value; return this; }
        private PayloadBuilder category(String value) { category = value; return this; }
        private PayloadBuilder riskLevel(String value) { riskLevel = value; return this; }
        private PayloadBuilder sourceService(String value) { sourceService = value; return this; }
        private PayloadBuilder targetType(String value) { targetType = value; return this; }
        private PayloadBuilder phase(String value) { phase = value; return this; }
        private PayloadBuilder outcome(String value) { outcome = value; return this; }
        private PayloadBuilder reason(String value) { reason = value; return this; }
        private PayloadBuilder failureCode(String value) { failureCode = value; return this; }
        private PayloadBuilder actorType(String value) { actorType = value; return this; }
        private PayloadBuilder actorRoles(List<String> value) { actorRoles = value; return this; }
        private PayloadBuilder beforeSummary(Map<String, String> value) { beforeSummary = value; return this; }
        private PayloadBuilder afterSummary(Map<String, String> value) { afterSummary = value; return this; }
        private PayloadBuilder clientIpHash(String value) { clientIpHash = value; return this; }

        private OperationAuditPayloadV1 build() {
            return new OperationAuditPayloadV1(
                    schemaVersion,
                    "00000000-0000-4000-8000-000000000001",
                    "operation-1",
                    phase,
                    Instant.parse("2026-09-03T00:00:00Z"),
                    sourceService,
                    "1.0.0",
                    category,
                    operationCode,
                    riskLevel,
                    outcome,
                    reason,
                    failureCode,
                    actorType,
                    "actor-1",
                    actorRoles,
                    targetType,
                    "target-1",
                    "version-1",
                    beforeSummary,
                    afterSummary,
                    "trace-1",
                    "sw-trace-1",
                    "request-1",
                    "domain-task-1",
                    "related-event-1",
                    clientIpHash,
                    "b".repeat(64)
            );
        }
    }
}
