package com.leetmodel.assistant;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class AssistantProductionMigrationContractTest {

    @Test
    void v4KeepsProductionOwnershipInAssistantAndBackfillsMessageSnapshot() throws Exception {
        String sql;
        try (var stream = getClass().getResourceAsStream(
                "/db/migration/V4__add_production_workflow_configuration.sql")) {
            assertThat(stream).isNotNull();
            sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertThat(sql)
                .contains("CREATE TABLE `assistant_workflow_version`")
                .contains("CREATE TABLE `assistant_production_config`")
                .contains("CREATE TABLE `assistant_production_pointer`")
                .contains("CREATE TABLE `assistant_production_change_request`")
                .contains("CREATE TABLE `assistant_production_audit`")
                .contains("ASSISTANT_NO_RAG_V1", "ASSISTANT_RAG_V1")
                .contains("ADD COLUMN `production_config_version`")
                .contains("ADD COLUMN `production_revision`")
                .contains("ADD COLUMN `workflow_version`")
                .contains("WHERE `role` = 'ASSISTANT'");
        assertThat(sql.toUpperCase()).doesNotContain("DROP ", "DELETE ", "TRUNCATE");
    }
}
