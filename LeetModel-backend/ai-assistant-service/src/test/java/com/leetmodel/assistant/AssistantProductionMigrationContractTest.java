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

    @Test
    void v5CreatesVersionedToolCallAuditWithoutDestructiveStatements() throws Exception {
        String sql;
        try (var stream = getClass().getResourceAsStream(
                "/db/migration/V5__create_assistant_tool_call.sql")) {
            assertThat(stream).isNotNull();
            sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertThat(sql)
                .contains("CREATE TABLE `assistant_tool_call`")
                .contains("`attempt_no` INT NOT NULL")
                .contains("`sequence_no` INT NOT NULL")
                .contains("`toolset_version` VARCHAR(64) NOT NULL")
                .contains("`arguments_json` JSON NULL")
                .contains("`result_snapshot_json` JSON NULL")
                .contains("`planning_ai_call_id` VARCHAR(64) NULL")
                .contains("UNIQUE INDEX `uk_message_attempt_sequence`")
                .contains("INDEX `idx_tool_status_create_time`");
        assertThat(sql.toUpperCase()).doesNotContain("DROP ", "DELETE ", "TRUNCATE");
    }

    @Test
    void v6PublishesSeparateToolWorkflowsAndMessageSnapshots() throws Exception {
        String sql;
        try (var stream = getClass().getResourceAsStream(
                "/db/migration/V6__publish_assistant_tool_workflows.sql")) {
            assertThat(stream).isNotNull();
            sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertThat(sql)
                .contains("ADD COLUMN `toolset_version` VARCHAR(64) NULL")
                .contains("ADD COLUMN `attempt_count` INT NOT NULL DEFAULT 0")
                .contains("ASSISTANT_TOOLS_NO_RAG_V1", "ASSISTANT_TOOLS_RAG_V1")
                .contains("PROMPT_ASSISTANT_TOOLS_0001")
                .contains("MODEL_CFG_ASSISTANT_TOOLS_0001")
                .contains("ASSISTANT_TOOLSET_0001")
                .doesNotContain("UPDATE `assistant_workflow_version`")
                .doesNotContain("UPDATE `assistant_production_config`");
        assertThat(sql.toUpperCase()).doesNotContain("DROP ", "DELETE ", "TRUNCATE");
    }
}
