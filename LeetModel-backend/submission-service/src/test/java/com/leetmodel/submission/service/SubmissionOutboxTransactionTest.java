package com.leetmodel.submission.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.leetmodel.common.messaging.MessageCodec;
import com.leetmodel.common.messaging.MessageEnvelopeFactory;
import com.leetmodel.common.messaging.MessagingNamespace;
import com.leetmodel.common.messaging.internal.JdbcMessageOutbox;
import com.leetmodel.submission.entity.Submission;
import com.leetmodel.submission.entity.SubmissionUpload;
import com.leetmodel.submission.mapper.SubmissionMapper;
import com.leetmodel.submission.mapper.SubmissionUploadMapper;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SubmissionOutboxTransactionTest {

    private JdbcTemplate jdbcTemplate;
    private TransactionTemplate transactionTemplate;
    private SubmissionUploadPersistenceService service;
    private SubmissionUpload upload;
    private SubmissionMapper submissionMapper;

    @BeforeEach
    void setUp() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:submission-outbox;MODE=MySQL;DB_CLOSE_DELAY=-1");
        jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("DROP ALL OBJECTS");
        jdbcTemplate.execute("CREATE TABLE domain_submission (id BIGINT PRIMARY KEY, team_id BIGINT NOT NULL)");
        jdbcTemplate.execute("""
                CREATE TABLE message_outbox (
                  event_id VARCHAR(36) PRIMARY KEY, topic VARCHAR(255), tag VARCHAR(80),
                  message_key VARCHAR(255), event_type VARCHAR(100), schema_version INT,
                  source_service VARCHAR(100), aggregate_type VARCHAR(100), aggregate_id VARCHAR(100),
                  idempotency_key VARCHAR(255), trace_id VARCHAR(100), payload_json TEXT,
                  status VARCHAR(20), retry_count INT, next_attempt_at TIMESTAMP,
                  lease_owner VARCHAR(160), lease_expires_at TIMESTAMP, broker_message_id VARCHAR(255),
                  last_error VARCHAR(500), occurred_at TIMESTAMP, published_at TIMESTAMP,
                  create_time TIMESTAMP, update_time TIMESTAMP,
                  UNIQUE(event_type, idempotency_key))
                """);
        transactionTemplate = new TransactionTemplate(new DataSourceTransactionManager(dataSource));

        SubmissionUploadMapper uploadMapper = mock(SubmissionUploadMapper.class);
        submissionMapper = mock(SubmissionMapper.class);
        upload = new SubmissionUpload();
        upload.setId(1L);
        upload.setTeamId(2L);
        upload.setProblemId(3L);
        upload.setUploaderId(4L);
        upload.setOriginalFilename("paper.pdf");
        upload.setFinalObjectName("submissions/2/paper.pdf");
        upload.setFileSize(100L);
        when(uploadMapper.selectForUpdate(1L)).thenReturn(upload);
        when(submissionMapper.selectMaxVersion(2L)).thenReturn(0);
        doAnswer(invocation -> {
            Submission submission = invocation.getArgument(0);
            submission.setId(101L);
            return jdbcTemplate.update("INSERT INTO domain_submission(id, team_id) VALUES (?, ?)",
                    submission.getId(), submission.getTeamId());
        }).when(submissionMapper).insert(any(Submission.class));

        Clock clock = Clock.fixed(Instant.parse("2026-09-01T00:00:00Z"), ZoneOffset.UTC);
        MessageCodec codec = new MessageCodec(
                new ObjectMapper().registerModule(new JavaTimeModule()), MessageCodec.MAX_PAYLOAD_BYTES);
        service = new SubmissionUploadPersistenceService(uploadMapper, submissionMapper,
                new MessageEnvelopeFactory("submission-service", clock),
                new JdbcMessageOutbox(jdbcTemplate, codec, new MessagingNamespace("lm-test"), clock));
    }

    @Test
    void commitSubmissionAndOutboxTogether() {
        Submission submission = transactionTemplate.execute(status -> service.createSubmission(1L));

        assertThat(submission).isNotNull();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM domain_submission", Long.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM message_outbox", Long.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM message_outbox", String.class))
                .isEqualTo("PENDING");
    }

    @Test
    void rollbackSubmissionAndOutboxTogether() {
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            service.createSubmission(1L);
            throw new IllegalStateException("simulate transaction failure");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM domain_submission", Long.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM message_outbox", Long.class)).isZero();
    }

    @Test
    void repairMissingOutboxForSubmissionCreatedBeforeUpgradeAndRemainIdempotent() {
        Submission existing = new Submission();
        existing.setId(101L);
        existing.setTeamId(2L);
        existing.setProblemId(3L);
        upload.setSubmissionId(101L);
        when(submissionMapper.selectById(101L)).thenReturn(existing);

        transactionTemplate.executeWithoutResult(status -> service.createSubmission(1L));
        transactionTemplate.executeWithoutResult(status -> service.createSubmission(1L));

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM message_outbox", Long.class)).isEqualTo(1);
    }
}
