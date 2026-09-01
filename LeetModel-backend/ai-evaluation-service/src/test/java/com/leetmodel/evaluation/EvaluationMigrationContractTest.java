package com.leetmodel.evaluation;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class EvaluationMigrationContractTest {

    @Test
    void v2UsesAdditiveBackfillBeforeTighteningAndKeepsLegacyScores() throws IOException {
        String sql;
        try (var stream = getClass().getResourceAsStream(
                "/db/migration/V2__generalize_evaluation_domain.sql")) {
            assertThat(stream).isNotNull();
            sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertThat(sql).contains("ADD COLUMN `feature_code` VARCHAR(32) NULL")
                .contains("SET `feature_code` = 'REVIEW'")
                .contains("MODIFY COLUMN `feature_code` VARCHAR(32) NOT NULL")
                .contains("LEGACY_REVIEW_METRICS_V1")
                .contains("REVIEW_SUBMISSION_V1")
                .contains("idx_task_slot_attempt")
                .contains("idx_ai_call_id");
        assertThat(sql.toUpperCase()).doesNotContain("DROP TABLE", "DROP COLUMN", "TRUNCATE");
        assertThat(sql).doesNotContain("SET `overall_score`");
    }

    @Test
    void v3BackfillsAttemptScopedIdempotencyBeforeUniqueConstraint() throws IOException {
        String sql;
        try (var stream = getClass().getResourceAsStream(
                "/db/migration/V3__add_evaluation_attempt_idempotency.sql")) {
            assertThat(stream).isNotNull();
            sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertThat(sql).contains("ADD COLUMN `idempotency_key` VARCHAR(128) NULL")
                .contains("SET `idempotency_key` = CONCAT")
                .contains("MODIFY COLUMN `idempotency_key` VARCHAR(128) NOT NULL")
                .contains("uk_evaluation_attempt_idempotency");
        assertThat(sql.toUpperCase()).doesNotContain("DROP TABLE", "DROP COLUMN", "TRUNCATE");
    }

    @Test
    void v4AddsControlAuditWithoutDeletingHistory() throws IOException {
        String sql;
        try (var stream = getClass().getResourceAsStream(
                "/db/migration/V4__add_evaluation_task_control_audit.sql")) {
            assertThat(stream).isNotNull();
            sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertThat(sql).contains("`last_operated_by`", "`last_operation`", "`last_operated_at`");
        assertThat(sql.toUpperCase()).doesNotContain("DELETE ", "DROP TABLE", "DROP COLUMN", "TRUNCATE");
    }

    @Test
    void v5AddsRawMetricsSnapshotWithoutReplacingLegacyColumns() throws IOException {
        String sql;
        try (var stream = getClass().getResourceAsStream(
                "/db/migration/V5__add_evaluation_raw_metrics.sql")) {
            assertThat(stream).isNotNull();
            sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertThat(sql).contains("ADD COLUMN `raw_metrics_json` LONGTEXT NULL");
        assertThat(sql.toUpperCase()).doesNotContain("DROP ", "DELETE ", "TRUNCATE", "OVERALL_SCORE =");
    }

    @Test
    void v6BackfillsImmutableDatasetVersionForComparison() throws IOException {
        String sql;
        try (var stream = getClass().getResourceAsStream(
                "/db/migration/V6__snapshot_evaluation_dataset_version.sql")) {
            assertThat(stream).isNotNull();
            sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertThat(sql).contains("ADD COLUMN `dataset_version`", "JOIN `evaluation_dataset`",
                "MODIFY COLUMN `dataset_version` VARCHAR(64) NOT NULL", "idx_comparison_criteria");
        assertThat(sql.toUpperCase()).doesNotContain("DROP ", "DELETE ", "TRUNCATE");
    }

    @Test
    void v7KeepsVersionedWeightConfigurationInEvaluationDatabase() throws IOException {
        String sql;
        try (var stream = getClass().getResourceAsStream(
                "/db/migration/V7__create_evaluation_weight_scheme.sql")) {
            assertThat(stream).isNotNull();
            sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertThat(sql)
                .contains("CREATE TABLE `evaluation_weight_scheme`")
                .contains("CREATE TABLE `evaluation_weight_scheme_item`")
                .contains("UNIQUE INDEX `uk_scheme_version`")
                .contains("UNIQUE INDEX `uk_scheme_metric`")
                .contains("`feature_code` VARCHAR(32) NOT NULL")
                .contains("`metric_version` VARCHAR(64) NOT NULL")
                .contains("`normalization_version` VARCHAR(64) NOT NULL")
                .contains("`weight_percent` DECIMAL(7,4) NOT NULL");
        assertThat(sql.toUpperCase()).doesNotContain("DROP ", "DELETE ", "TRUNCATE");
    }

    @Test
    void v8AddsImmutableScoreResultsWithoutRewritingRawOrLegacyScores() throws IOException {
        String sql;
        try (var stream = getClass().getResourceAsStream(
                "/db/migration/V8__add_evaluation_score_result.sql")) {
            assertThat(stream).isNotNull();
            sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertThat(sql)
                .contains("ADD COLUMN `weight_scheme_snapshot_json` LONGTEXT NULL")
                .contains("CREATE TABLE `evaluation_score_result`")
                .contains("CREATE TABLE `evaluation_score_result_item`")
                .contains("`raw_metrics_snapshot_json` LONGTEXT NOT NULL")
                .contains("`version_selection_index` DECIMAL(9,6) NULL")
                .contains("UNIQUE INDEX `uk_task_score_version`")
                .contains("UNIQUE INDEX `uk_score_result_metric`");
        assertThat(sql.toUpperCase())
                .doesNotContain("DROP ", "DELETE ", "TRUNCATE", "SET `RAW_METRICS_JSON`", "SET `OVERALL_SCORE`");
    }

    @Test
    void v9AddsReliableDispatchFactsWithoutRewritingEvaluationHistory() throws IOException {
        String sql;
        try (var stream = getClass().getResourceAsStream(
                "/db/migration/V9__add_reliable_evaluation_dispatch.sql")) {
            assertThat(stream).isNotNull();
            sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertThat(sql)
                .contains("CREATE TABLE `message_inbox`")
                .contains("CREATE TABLE `message_outbox`")
                .contains("ADD COLUMN `trace_id`")
                .contains("ADD COLUMN `next_run_at`")
                .contains("ADD COLUMN `lease_token`")
                .contains("ADD COLUMN `recovery_count`")
                .contains("idx_evaluation_run_claim")
                .contains("idx_evaluation_run_reconcile");
        assertThat(sql.toUpperCase()).doesNotContain("DROP ", "DELETE ", "TRUNCATE");
    }
}
