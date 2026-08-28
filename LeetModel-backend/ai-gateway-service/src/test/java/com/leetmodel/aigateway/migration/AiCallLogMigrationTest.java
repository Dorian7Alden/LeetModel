package com.leetmodel.aigateway.migration;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class AiCallLogMigrationTest {

    @Test
    void v2ShouldBackfillHistoryAndCreateRequiredTraceIndexes() throws Exception {
        String sql;
        try (var input = getClass().getResourceAsStream(
                "/db/migration/V2__extend_ai_call_log_context_and_cost.sql")) {
            assertThat(input).isNotNull();
            sql = new String(input.readAllBytes(), StandardCharsets.UTF_8).toLowerCase();
        }
        try (var input = getClass().getResourceAsStream("/db/migration/V1__create_ai_call_log.sql")) {
            assertThat(input).isNotNull();
            sql += new String(input.readAllBytes(), StandardCharsets.UTF_8).toLowerCase();
        }

        assertThat(sql).contains("add column `new_api_request_id`")
                .contains("add column `business_task_id`")
                .contains("add column `evaluation_task_id`")
                .contains("add column `queue_ms`")
                .contains("add column `execution_ms`")
                .contains("add column `cost_amount`")
                .contains("set `modality` = case")
                .contains("`input_tokens` = `prompt_tokens`")
                .contains("idx_business_task_time")
                .contains("idx_evaluation_task_time")
                .contains("idx_status_create_time");
        assertThat(sql).doesNotContain("prompt_body", "response_body", "paper_content", "knowledge_content");
    }

    @Test
    void costUpdateMustBeConditionalAndMustNotAccumulate() throws Exception {
        String mapperSource;
        try (var input = getClass().getResourceAsStream(
                "/db/migration/V3__add_cost_enrichment_state.sql")) {
            assertThat(input).isNotNull();
            mapperSource = new String(input.readAllBytes(), StandardCharsets.UTF_8).toLowerCase();
        }
        String javaSource = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/com/leetmodel/aigateway/mapper/AiCallLogMapper.java"));

        assertThat(mapperSource).contains("idx_cost_enrichment_due", "cost_enrichment_attempts");
        assertThat(javaSource).contains("SET cost_amount = #{amount}")
                .contains("cost_source = 'UNKNOWN'")
                .doesNotContain("cost_amount + #{amount}");
    }
}
