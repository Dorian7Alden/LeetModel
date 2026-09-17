package com.leetmodel.evaluation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.leetmodel.evaluation.entity.EvaluationRunAttempt;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public interface EvaluationRunAttemptMapper extends BaseMapper<EvaluationRunAttempt> {

    @Update("UPDATE evaluation_run_attempt SET last_wakeup_at = #{now}, update_time = #{now} "
            + "WHERE id = #{id} AND task_id = #{taskId} AND attempt_no = #{attemptNo} "
            + "AND status = 'WAITING' AND deleted = 0")
    int markWakeup(@Param("id") Long id, @Param("taskId") Long taskId,
                   @Param("attemptNo") Integer attemptNo, @Param("now") LocalDateTime now);

    @Update("UPDATE evaluation_run_attempt r SET r.status = 'RUNNING', "
            + "r.started_at = COALESCE(r.started_at, #{now}), r.lease_owner = #{owner}, "
            + "r.lease_token = #{token}, r.lease_expires_at = #{leaseExpiresAt}, "
            + "r.heartbeat_at = #{now}, r.update_time = #{now} "
            + "WHERE r.id = #{id} AND r.status = 'WAITING' AND r.next_run_at <= #{now} "
            + "AND r.deleted = 0 AND EXISTS (SELECT 1 FROM evaluation_task t "
            + "WHERE t.id = r.task_id AND t.deleted = 0 AND t.status IN ('WAITING','RUNNING'))")
    int claim(@Param("id") Long id, @Param("owner") String owner, @Param("token") String token,
              @Param("now") LocalDateTime now, @Param("leaseExpiresAt") LocalDateTime leaseExpiresAt);

    @Update("UPDATE evaluation_run_attempt SET heartbeat_at = #{now}, "
            + "lease_expires_at = #{leaseExpiresAt}, update_time = #{now} "
            + "WHERE id = #{id} AND status = 'RUNNING' AND lease_owner = #{owner} "
            + "AND lease_token = #{token} AND deleted = 0")
    int heartbeat(@Param("id") Long id, @Param("owner") String owner, @Param("token") String token,
                  @Param("now") LocalDateTime now, @Param("leaseExpiresAt") LocalDateTime leaseExpiresAt);

    @Update("UPDATE evaluation_run_attempt SET status = 'WAITING', lease_owner = NULL, "
            + "lease_token = NULL, lease_expires_at = NULL, heartbeat_at = NULL, update_time = #{now} "
            + "WHERE id = #{id} AND status = 'RUNNING' AND lease_token = #{token} AND deleted = 0")
    int releaseClaim(@Param("id") Long id, @Param("token") String token,
                     @Param("now") LocalDateTime now);

    @Update("UPDATE evaluation_run_attempt SET status = 'SUCCEEDED', failure_type = NULL, "
            + "score = #{score}, result_json = #{resultJson}, metrics_json = #{metricsJson}, "
            + "model_name = #{modelName}, model_execution_config_version = #{modelConfigVersion}, "
            + "rag_index_version = #{ragIndexVersion}, "
            + "ai_call_id = #{aiCallId}, duration_ms = #{durationMs}, error_message = NULL, "
            + "finished_at = #{now}, lease_owner = NULL, lease_token = NULL, "
            + "lease_expires_at = NULL, heartbeat_at = NULL, update_time = #{now} "
            + "WHERE id = #{id} AND status = 'RUNNING' AND lease_token = #{token} AND deleted = 0")
    int succeed(@Param("id") Long id, @Param("token") String token, @Param("score") BigDecimal score,
                @Param("resultJson") String resultJson, @Param("metricsJson") String metricsJson,
                @Param("modelName") String modelName,
                @Param("modelConfigVersion") String modelConfigVersion,
                @Param("ragIndexVersion") String ragIndexVersion,
                @Param("aiCallId") String aiCallId, @Param("durationMs") Long durationMs,
                @Param("now") LocalDateTime now);

    @Update("UPDATE evaluation_run_attempt SET status = 'FAILED', failure_type = #{failureType}, "
            + "score = NULL, result_json = NULL, metrics_json = NULL, model_name = NULL, "
            + "ai_call_id = #{aiCallId}, "
            + "duration_ms = #{durationMs}, error_message = #{errorMessage}, finished_at = #{now}, "
            + "lease_owner = NULL, lease_token = NULL, lease_expires_at = NULL, heartbeat_at = NULL, "
            + "update_time = #{now} WHERE id = #{id} AND status = 'RUNNING' "
            + "AND lease_token = #{token} AND deleted = 0")
    int fail(@Param("id") Long id, @Param("token") String token,
             @Param("failureType") String failureType,
             @Param("aiCallId") String aiCallId,
             @Param("durationMs") Long durationMs, @Param("errorMessage") String errorMessage,
             @Param("now") LocalDateTime now);

    @Update("UPDATE evaluation_run_attempt SET status = 'WAITING', started_at = NULL, "
            + "next_run_at = #{nextRunAt}, duration_ms = #{durationMs}, error_message = #{message}, "
            + "lease_owner = NULL, lease_token = NULL, lease_expires_at = NULL, heartbeat_at = NULL, "
            + "update_time = #{now} WHERE id = #{id} AND status = 'RUNNING' "
            + "AND lease_token = #{token} AND deleted = 0")
    int deferPending(@Param("id") Long id, @Param("token") String token,
                     @Param("durationMs") Long durationMs, @Param("message") String message,
                     @Param("nextRunAt") LocalDateTime nextRunAt, @Param("now") LocalDateTime now);

    @Update("UPDATE evaluation_run_attempt SET status = 'UNKNOWN', failure_type = 'UNKNOWN', "
            + "ai_call_id = #{aiCallId}, duration_ms = #{durationMs}, error_message = #{message}, "
            + "finished_at = #{now}, lease_owner = NULL, lease_token = NULL, "
            + "lease_expires_at = NULL, heartbeat_at = NULL, update_time = #{now} "
            + "WHERE id = #{id} AND status = 'RUNNING' AND lease_token = #{token} AND deleted = 0")
    int markUnknown(@Param("id") Long id, @Param("token") String token,
                    @Param("aiCallId") String aiCallId,
                    @Param("durationMs") Long durationMs, @Param("message") String message,
                    @Param("now") LocalDateTime now);

    @Select("SELECT * FROM evaluation_run_attempt WHERE status = 'RUNNING' "
            + "AND lease_expires_at < #{now} AND deleted = 0 ORDER BY lease_expires_at ASC")
    List<EvaluationRunAttempt> selectExpired(@Param("now") LocalDateTime now);

    @Update("UPDATE evaluation_run_attempt SET status = 'UNKNOWN', failure_type = 'UNKNOWN', "
            + "error_message = '评价进程中断且上游结果未知，禁止自动重试', "
            + "recovery_count = recovery_count + 1, finished_at = #{now}, "
            + "lease_owner = NULL, lease_token = NULL, lease_expires_at = NULL, heartbeat_at = NULL, "
            + "update_time = #{now} WHERE id = #{id} AND status = 'RUNNING' "
            + "AND lease_expires_at < #{now} AND deleted = 0")
    int markExpiredUnknown(@Param("id") Long id, @Param("now") LocalDateTime now);

    @Update("UPDATE evaluation_run_attempt SET status = 'CANCELLED', failure_type = NULL, "
            + "error_message = '任务已取消，槽位未派发', finished_at = #{now}, update_time = #{now} "
            + "WHERE task_id = #{taskId} AND status = 'WAITING' AND deleted = 0")
    int cancelWaiting(@Param("taskId") Long taskId, @Param("now") LocalDateTime now);

    @Select("SELECT r.* FROM evaluation_run_attempt r JOIN evaluation_task t ON t.id = r.task_id "
            + "WHERE r.status = 'WAITING' AND r.next_run_at <= #{now} AND r.deleted = 0 "
            + "AND t.deleted = 0 AND t.status IN ('WAITING','RUNNING') "
            + "AND (r.last_wakeup_event_at IS NULL OR r.last_wakeup_event_at < #{before}) "
            + "AND NOT EXISTS (SELECT 1 FROM message_outbox mo WHERE mo.aggregate_type = 'evaluation-slot' "
            + "AND mo.aggregate_id = CAST(r.id AS CHAR) AND mo.status IN ('PENDING','SENDING','BLOCKED')) "
            + "ORDER BY r.next_run_at, r.id LIMIT #{limit}")
    List<EvaluationRunAttempt> selectReconciliationCandidates(@Param("now") LocalDateTime now,
                                                               @Param("before") LocalDateTime before,
                                                               @Param("limit") int limit);

    @Update("UPDATE evaluation_run_attempt SET last_wakeup_event_at = #{now}, update_time = #{now} "
            + "WHERE id = #{id} AND status = 'WAITING' AND next_run_at <= #{now} AND deleted = 0")
    int markWakeupEvent(@Param("id") Long id, @Param("now") LocalDateTime now);
}
