package com.leetmodel.audit;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AuditMigrationContractTest {

    @Test
    void shouldDefineOnlyAppendArchiveAndInvestigationIndexes() throws IOException {
        String migration = read("db/migration/V1__create_audit_archive.sql");

        assertThat(migration).contains("CREATE TABLE `message_inbox`");
        assertThat(migration).contains("CREATE TABLE `operation_audit_event`");
        assertThat(migration).doesNotContain("UPDATE `operation_audit_event`", "DELETE FROM `operation_audit_event`");
        assertThat(migration).contains("PRIMARY KEY (`audit_event_id`)");
        for (String index : List.of(
                "idx_audit_operation_timeline",
                "idx_audit_actor_timeline",
                "idx_audit_target_timeline",
                "idx_audit_operation_outcome_time",
                "idx_audit_trace",
                "idx_audit_sw_trace"
        )) {
            assertThat(migration).contains(index);
        }
        assertThat(migration).contains("chk_audit_schema_version", "chk_audit_phase_outcome");
        assertThat(migration).contains("JSON_TYPE(`before_summary_json`) = 'OBJECT'");
        assertThat(migration).contains("JSON_TYPE(`after_summary_json`) = 'OBJECT'");
        assertThat(migration).contains("JSON_TYPE(`actor_roles_json`) = 'ARRAY'");
    }

    @Test
    void shouldUseDedicatedSchemaAndExternalizedCredentials() throws IOException {
        String application = read("application.yml");

        assertThat(application).contains("lm_audit", "AUDIT_DB_APP_PASSWORD", "AUDIT_DB_MIGRATOR_PASSWORD");
        assertThat(application).doesNotContain("lm_user", "lm_problem", "lm_submission", "root");
        assertThat(application).contains("clean-disabled: true", "baseline-on-migrate: false");
        assertThat(application).contains("connect-retries: 0");
    }

    private String read(String resource) throws IOException {
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertThat(stream).as("classpath resource %s", resource).isNotNull();
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
