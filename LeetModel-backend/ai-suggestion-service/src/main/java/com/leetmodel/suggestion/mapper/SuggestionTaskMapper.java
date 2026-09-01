package com.leetmodel.suggestion.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.leetmodel.suggestion.entity.SuggestionTask;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

public interface SuggestionTaskMapper extends BaseMapper<SuggestionTask> {

    @Update("""
            UPDATE suggestion_task SET last_wakeup_at = #{now}, update_time = #{now}
            WHERE id = #{id} AND submission_id = #{submissionId}
              AND workflow_version = #{workflowVersion} AND deleted = 0
              AND status IN ('WAITING', 'LEASED', 'RUNNING')
            """)
    int markWakeup(@Param("id") Long id, @Param("submissionId") Long submissionId,
                   @Param("workflowVersion") String workflowVersion, @Param("now") LocalDateTime now);

    @Update("""
            UPDATE suggestion_task
            SET recovery_count = recovery_count + IF(status IN ('LEASED', 'RUNNING'), 1, 0),
                status = 'LEASED', lease_owner = #{owner}, lease_token = #{token},
                lease_expires_at = #{leaseExpiresAt}, heartbeat_at = #{now}, update_time = #{now}
            WHERE id = #{id} AND deleted = 0
              AND ((status = 'WAITING' AND next_run_at <= #{now})
                OR (status IN ('LEASED', 'RUNNING') AND lease_expires_at < #{now}))
            """)
    int claim(@Param("id") Long id, @Param("owner") String owner, @Param("token") String token,
              @Param("now") LocalDateTime now, @Param("leaseExpiresAt") LocalDateTime leaseExpiresAt);

    @Update("""
            UPDATE suggestion_task
            SET status = 'RUNNING', started_at = COALESCE(started_at, #{now}),
                heartbeat_at = #{now}, lease_expires_at = #{leaseExpiresAt},
                ai_idempotency_key = #{aiIdempotencyKey}, update_time = #{now}
            WHERE id = #{id} AND status = 'LEASED' AND lease_owner = #{owner}
              AND lease_token = #{token} AND deleted = 0
            """)
    int markRunning(@Param("id") Long id, @Param("owner") String owner, @Param("token") String token,
                    @Param("aiIdempotencyKey") String aiIdempotencyKey, @Param("now") LocalDateTime now,
                    @Param("leaseExpiresAt") LocalDateTime leaseExpiresAt);

    @Update("""
            UPDATE suggestion_task
            SET heartbeat_at = #{now}, lease_expires_at = #{leaseExpiresAt}, update_time = #{now}
            WHERE id = #{id} AND lease_owner = #{owner} AND lease_token = #{token}
              AND status IN ('LEASED', 'RUNNING') AND deleted = 0
            """)
    int heartbeat(@Param("id") Long id, @Param("owner") String owner, @Param("token") String token,
                  @Param("now") LocalDateTime now, @Param("leaseExpiresAt") LocalDateTime leaseExpiresAt);

    @Update("""
            UPDATE suggestion_task SET status = 'WAITING', lease_owner = NULL, lease_token = NULL,
                lease_expires_at = NULL, heartbeat_at = NULL, update_time = NOW(3)
            WHERE id = #{id} AND status = 'LEASED' AND lease_token = #{token}
            """)
    int releaseClaim(@Param("id") Long id, @Param("token") String token);

    @Update("""
            UPDATE suggestion_task SET current_stage = #{stage}, update_time = NOW(3)
            WHERE id = #{id} AND status = 'RUNNING' AND lease_token = #{token}
            """)
    int updateStage(@Param("id") Long id, @Param("token") String token, @Param("stage") String stage);

    @Update("""
            UPDATE suggestion_task SET parse_artifact_id = #{parseArtifactId},
                current_stage = 'PREPARING_REVIEW', update_time = NOW(3)
            WHERE id = #{id} AND status = 'RUNNING' AND lease_token = #{token}
            """)
    int saveParse(@Param("id") Long id, @Param("token") String token,
                  @Param("parseArtifactId") Long parseArtifactId);

    @Update("""
            UPDATE suggestion_task SET evidence_review_task_id = #{evidenceReviewTaskId},
                update_time = NOW(3)
            WHERE id = #{id} AND status = 'RUNNING' AND lease_token = #{token}
            """)
    int saveEvidenceTask(@Param("id") Long id, @Param("token") String token,
                         @Param("evidenceReviewTaskId") Long evidenceReviewTaskId);

    @Update("""
            UPDATE suggestion_task
            SET evidence_review_task_id = #{evidenceReviewTaskId},
                review_workflow_version = #{reviewWorkflowVersion},
                review_evidence_projection_version = #{projectionVersion},
                current_stage = 'RETRIEVING', update_time = NOW(3)
            WHERE id = #{id} AND status = 'RUNNING' AND lease_token = #{token}
            """)
    int saveReviewEvidence(@Param("id") Long id, @Param("token") String token,
                           @Param("evidenceReviewTaskId") Long evidenceReviewTaskId,
                           @Param("reviewWorkflowVersion") String reviewWorkflowVersion,
                           @Param("projectionVersion") String projectionVersion);

    @Update("""
            UPDATE suggestion_task
            SET retrieval_run_id = #{retrievalRunId}, knowledge_snapshot_json = #{knowledgeSnapshotJson},
                current_stage = 'GENERATING', update_time = NOW(3)
            WHERE id = #{id} AND status = 'RUNNING' AND lease_token = #{token}
            """)
    int saveKnowledge(@Param("id") Long id, @Param("token") String token,
                      @Param("retrievalRunId") String retrievalRunId,
                      @Param("knowledgeSnapshotJson") String knowledgeSnapshotJson);

    @Update("""
            UPDATE suggestion_task
            SET status = 'WAITING', current_stage = 'PREPARING_REVIEW', next_run_at = #{nextRunAt},
                started_at = NULL, failure_type = NULL, error_message = NULL,
                lease_owner = NULL, lease_token = NULL, lease_expires_at = NULL,
                heartbeat_at = NULL, update_time = NOW(3)
            WHERE id = #{id} AND status = 'RUNNING' AND lease_token = #{token}
            """)
    int waitForEvidence(@Param("id") Long id, @Param("token") String token,
                        @Param("nextRunAt") LocalDateTime nextRunAt);

    @Update("""
            UPDATE suggestion_task
            SET status = 'WAITING', retry_count = retry_count + 1, attempt_no = attempt_no + 1,
                current_stage = 'PREPARING', next_run_at = #{nextRunAt}, started_at = NULL,
                finished_at = NULL, failure_type = #{failureType}, error_message = #{errorMessage},
                ai_idempotency_key = #{nextIdempotencyKey}, lease_owner = NULL, lease_token = NULL,
                lease_expires_at = NULL, heartbeat_at = NULL, update_time = NOW(3)
            WHERE id = #{id} AND status = 'RUNNING' AND lease_token = #{token}
            """)
    int scheduleRetry(@Param("id") Long id, @Param("token") String token,
                      @Param("nextRunAt") LocalDateTime nextRunAt,
                      @Param("failureType") String failureType, @Param("errorMessage") String errorMessage,
                      @Param("nextIdempotencyKey") String nextIdempotencyKey);

    @Update("""
            UPDATE suggestion_task
            SET status = 'WAITING', next_run_at = #{nextRunAt}, failure_type = #{failureType},
                error_message = #{errorMessage}, lease_owner = NULL, lease_token = NULL,
                lease_expires_at = NULL, heartbeat_at = NULL, update_time = NOW(3)
            WHERE id = #{id} AND status = 'RUNNING' AND lease_token = #{token}
            """)
    int scheduleSameAttempt(@Param("id") Long id, @Param("token") String token,
                            @Param("nextRunAt") LocalDateTime nextRunAt,
                            @Param("failureType") String failureType, @Param("errorMessage") String errorMessage);

    @Update("""
            UPDATE suggestion_task
            SET status = #{status}, failure_type = #{failureType}, error_message = #{errorMessage},
                finished_at = NOW(3), lease_owner = NULL, lease_token = NULL,
                lease_expires_at = NULL, heartbeat_at = NULL, update_time = NOW(3)
            WHERE id = #{id} AND status = 'RUNNING' AND lease_token = #{token}
            """)
    int markTerminalFailure(@Param("id") Long id, @Param("token") String token,
                            @Param("status") String status, @Param("failureType") String failureType,
                            @Param("errorMessage") String errorMessage);

    @Update("""
            UPDATE suggestion_task
            SET status = 'COMPLETED', current_stage = 'COMPLETED', result_json = #{resultJson},
                model_name = #{modelName}, ai_call_id = #{aiCallId}, finished_at = #{finishedAt},
                failure_type = NULL, error_message = NULL, lease_owner = NULL, lease_token = NULL,
                lease_expires_at = NULL, heartbeat_at = NULL, update_time = #{finishedAt}
            WHERE id = #{id} AND status = 'RUNNING' AND lease_token = #{token}
            """)
    int complete(@Param("id") Long id, @Param("token") String token,
                 @Param("resultJson") String resultJson, @Param("modelName") String modelName,
                 @Param("aiCallId") String aiCallId, @Param("finishedAt") LocalDateTime finishedAt);

    @Update("""
            UPDATE suggestion_task
            SET status = 'WAITING', retry_count = retry_count + 1, attempt_no = attempt_no + 1,
                current_stage = 'PREPARING', next_run_at = #{now}, started_at = NULL,
                finished_at = NULL, failure_type = NULL, error_message = NULL,
                result_json = NULL, model_name = NULL, ai_call_id = NULL,
                ai_idempotency_key = #{nextIdempotencyKey}, lease_owner = NULL, lease_token = NULL,
                lease_expires_at = NULL, heartbeat_at = NULL, update_time = #{now}
            WHERE id = #{id} AND status = 'FAILED' AND deleted = 0
            """)
    int resetForRetry(@Param("id") Long id, @Param("now") LocalDateTime now,
                      @Param("nextIdempotencyKey") String nextIdempotencyKey);

    @Select("""
            SELECT * FROM suggestion_task
            WHERE deleted = 0
              AND ((status = 'WAITING' AND next_run_at <= #{now})
                OR (status IN ('LEASED', 'RUNNING') AND lease_expires_at < #{now}))
              AND (last_wakeup_event_at IS NULL OR last_wakeup_event_at < #{before})
              AND NOT EXISTS (
                SELECT 1 FROM message_outbox mo
                WHERE mo.aggregate_type = 'suggestion-task'
                  AND mo.aggregate_id = CAST(suggestion_task.id AS CHAR)
                  AND mo.status IN ('PENDING', 'SENDING', 'BLOCKED'))
            ORDER BY next_run_at, id LIMIT #{limit}
            """)
    List<SuggestionTask> selectReconciliationCandidates(@Param("now") LocalDateTime now,
                                                        @Param("before") LocalDateTime before,
                                                        @Param("limit") int limit);

    @Update("""
            UPDATE suggestion_task SET last_wakeup_event_at = #{now}, update_time = #{now}
            WHERE id = #{id} AND deleted = 0
              AND ((status = 'WAITING' AND next_run_at <= #{now})
                OR (status IN ('LEASED', 'RUNNING') AND lease_expires_at < #{now}))
            """)
    int markWakeupEvent(@Param("id") Long id, @Param("now") LocalDateTime now);

    @Select("SELECT COUNT(*) FROM suggestion_task WHERE deleted = 0 AND status IN ('WAITING', 'LEASED', 'RUNNING')")
    long countActiveBacklog();

    @Select("SELECT MIN(create_time) FROM suggestion_task WHERE deleted = 0 AND status = 'WAITING' AND next_run_at <= #{now}")
    LocalDateTime selectOldestDue(@Param("now") LocalDateTime now);
}
