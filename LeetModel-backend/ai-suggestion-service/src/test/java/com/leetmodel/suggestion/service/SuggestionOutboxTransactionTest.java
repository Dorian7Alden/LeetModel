package com.leetmodel.suggestion.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.leetmodel.common.api.dto.ReviewSummaryDTO;
import com.leetmodel.common.api.dto.SubmissionReviewDTO;
import com.leetmodel.common.api.feign.KnowledgeRetrievalFeignClient;
import com.leetmodel.common.api.feign.ProblemFeignClient;
import com.leetmodel.common.api.feign.ReviewFeignClient;
import com.leetmodel.common.api.feign.SubmissionFeignClient;
import com.leetmodel.common.api.feign.TeamFeignClient;
import com.leetmodel.common.core.result.Result;
import com.leetmodel.common.messaging.MessageCodec;
import com.leetmodel.common.messaging.MessageEnvelopeFactory;
import com.leetmodel.common.messaging.MessagingNamespace;
import com.leetmodel.common.messaging.internal.JdbcMessageOutbox;
import com.leetmodel.suggestion.config.SuggestionWorkerProperties;
import com.leetmodel.suggestion.dto.SuggestionCreateRequest;
import com.leetmodel.suggestion.entity.SuggestionTask;
import com.leetmodel.suggestion.mapper.SuggestionTaskMapper;
import com.leetmodel.suggestion.messaging.SuggestionReadyMessageService;
import com.leetmodel.suggestion.service.evidence.ReviewEvidenceProjector;
import com.leetmodel.suggestion.workflow.SuggestionV1Workflow;
import com.leetmodel.suggestion.workflow.v2.GroundedSuggestionV2Workflow;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SuggestionOutboxTransactionTest {
    private JdbcTemplate jdbcTemplate;
    private TransactionTemplate transactionTemplate;
    private SuggestionService service;

    @BeforeEach
    void setUp() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:suggestion-outbox;MODE=MySQL;DB_CLOSE_DELAY=-1");
        jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("DROP ALL OBJECTS");
        jdbcTemplate.execute("CREATE TABLE domain_suggestion(id BIGINT PRIMARY KEY, submission_id BIGINT NOT NULL)");
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

        SuggestionTaskMapper taskMapper = mock(SuggestionTaskMapper.class);
        SubmissionFeignClient submission = mock(SubmissionFeignClient.class);
        ReviewFeignClient review = mock(ReviewFeignClient.class);
        TeamFeignClient team = mock(TeamFeignClient.class);
        GroundedSuggestionV2Workflow workflow = mock(GroundedSuggestionV2Workflow.class);
        when(submission.getForReview(101L)).thenReturn(Result.ok(
                new SubmissionReviewDTO(101L, 11L, 51L, 1, "paper.pdf")));
        when(review.getByTask(5001L)).thenReturn(Result.ok(new ReviewSummaryDTO(
                5001L, 101L, 11L, 51L, "COMPLETED", "EVIDENCE_REVIEW_V2",
                BigDecimal.valueOf(88), "{}", "model", "call", null, LocalDateTime.now())));
        when(team.getMemberIds(11L)).thenReturn(Result.ok(List.of(7L)));
        when(workflow.currentPrompt()).thenReturn("prompt-v2");
        doAnswer(invocation -> {
            SuggestionTask task = invocation.getArgument(0);
            task.setId(9001L);
            return jdbcTemplate.update("INSERT INTO domain_suggestion(id, submission_id) VALUES (?, ?)",
                    task.getId(), task.getSubmissionId());
        }).when(taskMapper).insert(any(SuggestionTask.class));

        Clock clock = Clock.fixed(Instant.parse("2026-09-01T00:00:00Z"), ZoneOffset.UTC);
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        SuggestionReadyMessageService messages = new SuggestionReadyMessageService(
                new MessageEnvelopeFactory("ai-suggestion-service", clock),
                new JdbcMessageOutbox(jdbcTemplate,
                        new MessageCodec(objectMapper, MessageCodec.MAX_PAYLOAD_BYTES),
                        new MessagingNamespace("lm-test"), clock));
        SuggestionWorkerProperties properties = new SuggestionWorkerProperties();
        service = new SuggestionService(taskMapper, submission, review,
                mock(ProblemFeignClient.class), team, mock(KnowledgeRetrievalFeignClient.class),
                mock(SuggestionV1Workflow.class), workflow, mock(ReviewEvidenceProjector.class),
                messages, properties, objectMapper);
    }

    @Test
    void commitTaskAndReadyOutboxTogether() {
        transactionTemplate.executeWithoutResult(status -> service.create(request(), 7L));

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM domain_suggestion", Long.class))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM message_outbox", Long.class))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT event_type FROM message_outbox", String.class))
                .isEqualTo("SUGGESTION_TASK_READY");
    }

    @Test
    void rollbackTaskAndReadyOutboxTogether() {
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            service.create(request(), 7L);
            throw new IllegalStateException("rollback");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM domain_suggestion", Long.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM message_outbox", Long.class)).isZero();
    }

    private SuggestionCreateRequest request() {
        SuggestionCreateRequest request = new SuggestionCreateRequest();
        request.setSubmissionId(101L);
        request.setReviewTaskId(5001L);
        request.setClientRequestId("manual_action_0001");
        return request;
    }
}
