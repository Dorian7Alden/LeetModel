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
}
