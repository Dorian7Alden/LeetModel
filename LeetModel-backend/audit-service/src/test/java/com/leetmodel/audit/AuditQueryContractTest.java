package com.leetmodel.audit;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class AuditQueryContractTest {
    @Test
    void queryMustBeReadOnlyBoundedAndExplicitlyOrdered() throws Exception {
        String repository = Files.readString(Path.of("src/main/java/com/leetmodel/audit/repository/AuditQueryRepository.java"), StandardCharsets.UTF_8);
        assertThat(repository).contains("MAX_LIMIT = 100", "ORDER BY occurred_at DESC, audit_event_id DESC",
                "OperationAuditCatalog.require", "from must be before to");
        assertThat(repository).doesNotContain("UPDATE operation_audit_event", "DELETE FROM operation_audit_event");
    }

    @Test
    void internalBoundaryMustUseDedicatedTokenAndAdminProxyMustBeRoleProtected() throws Exception {
        String filter = Files.readString(Path.of("src/main/java/com/leetmodel/audit/config/AuditInternalAccessFilter.java"), StandardCharsets.UTF_8);
        String controller = Files.readString(Path.of("../admin-service/src/main/java/com/leetmodel/admin/controller/AdminAuditController.java"), StandardCharsets.UTF_8);
        assertThat(filter).contains("X-LeetModel-Audit-Token", "MessageDigest.isEqual");
        assertThat(controller).contains("@SaCheckRole(\"admin\")", "/api/admin/audit");
    }
}
