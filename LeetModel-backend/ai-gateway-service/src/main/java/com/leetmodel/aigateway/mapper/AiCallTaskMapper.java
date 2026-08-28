package com.leetmodel.aigateway.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.leetmodel.aigateway.entity.AiCallTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AiCallTaskMapper extends BaseMapper<AiCallTask> {

    @Select("""
            SELECT * FROM ai_call_task
             WHERE deleted = 0 AND state = 'QUEUED'
             ORDER BY queued_at ASC
             LIMIT #{limit}
            """)
    List<AiCallTask> selectQueued(@Param("limit") int limit);

    @Select("SELECT * FROM ai_call_task WHERE deleted = 0 AND task_id = #{taskId} LIMIT 1")
    AiCallTask selectByTaskId(@Param("taskId") String taskId);

    @Select("""
            SELECT * FROM ai_call_task
             WHERE deleted = 0 AND state IN ('LEASED','RUNNING')
               AND lease_expiry IS NOT NULL AND lease_expiry < #{now}
             ORDER BY lease_expiry ASC
             LIMIT #{limit}
            """)
    List<AiCallTask> selectExpiredLeases(@Param("now") LocalDateTime now, @Param("limit") int limit);

    @Select("""
            <script>
            SELECT * FROM ai_call_task
             WHERE deleted = 0
             <if test='state != null and state != ""'>AND state = #{state}</if>
             <if test='priority != null and priority != ""'>AND effective_priority = #{priority}</if>
             <if test='callerService != null and callerService != ""'>AND caller_service = #{callerService}</if>
             ORDER BY CASE WHEN state IN ('QUEUED','LEASED','RUNNING') THEN 0 ELSE 1 END,
                      queued_at ASC
             LIMIT 500
            </script>
            """)
    List<AiCallTask> selectForMonitoring(@Param("state") String state,
                                         @Param("priority") String priority,
                                         @Param("callerService") String callerService);

    @Select("SELECT COUNT(*) FROM ai_call_task WHERE deleted = 0 AND state IN ('QUEUED','LEASED','RUNNING')")
    long countActive();

    @Select("""
            SELECT COUNT(*) FROM ai_call_task
             WHERE deleted = 0 AND state IN ('QUEUED','LEASED','RUNNING') AND effective_priority <> 'P0'
            """)
    long countActiveNonP0();

    @Select("""
            SELECT * FROM ai_call_task
             WHERE deleted = 0 AND caller_service = #{callerService} AND idempotency_key = #{idempotencyKey}
             LIMIT 1
            """)
    AiCallTask selectByIdempotency(@Param("callerService") String callerService,
                                   @Param("idempotencyKey") String idempotencyKey);

    @Update("""
            UPDATE ai_call_task
               SET state = 'LEASED', lease_owner = #{owner}, lease_expiry = #{leaseExpiry},
                   leased_at = #{now}, version = version + 1, update_time = #{now}
             WHERE task_id = #{taskId} AND deleted = 0 AND state = 'QUEUED' AND version = #{version}
               AND cancel_requested = 0 AND deadline > #{now}
            """)
    int claimQueued(@Param("taskId") String taskId, @Param("version") long version,
                    @Param("owner") String owner, @Param("leaseExpiry") LocalDateTime leaseExpiry,
                    @Param("now") LocalDateTime now);

    @Update("""
            UPDATE ai_call_task
               SET state = #{toState}, version = version + 1, update_time = #{now},
                   attempt_count = CASE WHEN #{toState} = 'RUNNING' THEN attempt_count + 1 ELSE attempt_count END,
                   started_at = CASE WHEN #{toState} = 'RUNNING' THEN #{now} ELSE started_at END,
                   finished_at = CASE WHEN #{toState} IN ('SUCCEEDED','FAILED','CANCELLED','EXPIRED')
                                      THEN #{now} ELSE finished_at END
             WHERE task_id = #{taskId} AND deleted = 0 AND state = #{fromState}
               AND version = #{version} AND (lease_owner = #{owner} OR #{owner} IS NULL)
            """)
    int transition(@Param("taskId") String taskId, @Param("version") long version,
                   @Param("fromState") String fromState, @Param("toState") String toState,
                   @Param("owner") String owner, @Param("now") LocalDateTime now);

    @Update("""
            UPDATE ai_call_task t
               SET t.state = 'QUEUED', t.lease_owner = NULL, t.lease_expiry = NULL,
                   t.version = t.version + 1, t.update_time = #{now}
             WHERE t.task_id = #{taskId} AND t.deleted = 0 AND t.state = 'LEASED'
               AND t.lease_expiry < #{now}
               AND NOT EXISTS (SELECT 1 FROM ai_call_attempt a
                                WHERE a.task_id = t.task_id AND a.deleted = 0)
            """)
    int releaseExpiredLeaseWithoutAttempt(@Param("taskId") String taskId,
                                          @Param("now") LocalDateTime now);

    @Update("""
            UPDATE ai_call_task t
               SET t.state = 'QUEUED', t.lease_owner = NULL, t.lease_expiry = NULL,
                   t.version = t.version + 1, t.update_time = #{now}
             WHERE t.task_id = #{taskId} AND t.deleted = 0 AND t.state IN ('LEASED','RUNNING')
               AND t.version = #{version} AND t.lease_expiry < #{now}
               AND NOT EXISTS (SELECT 1 FROM ai_call_attempt a
                                WHERE a.task_id = t.task_id AND a.deleted = 0
                                  AND a.state IN ('DISPATCHING','ACKNOWLEDGED','SUCCEEDED','UNKNOWN'))
            """)
    int requeueExpiredBeforeDispatch(@Param("taskId") String taskId, @Param("version") long version,
                                     @Param("now") LocalDateTime now);

    @Update("""
            UPDATE ai_call_task
               SET state = 'FAILED', error_code = 'AI_UPSTREAM_RESULT_UNKNOWN',
                   dead_letter_reason = 'LEASE_EXPIRED_AFTER_DISPATCH', request_payload = '',
                   lease_expiry = NULL, finished_at = #{now}, version = version + 1, update_time = #{now}
             WHERE task_id = #{taskId} AND deleted = 0 AND state = 'RUNNING'
               AND version = #{version} AND lease_expiry < #{now}
            """)
    int failExpiredRunningUnknown(@Param("taskId") String taskId, @Param("version") long version,
                                  @Param("now") LocalDateTime now);

    @Update("""
            UPDATE ai_call_task
               SET state = 'EXPIRED', error_code = 'AI_QUEUE_EXPIRED', finished_at = #{now},
                   version = version + 1, update_time = #{now}
             WHERE task_id = #{taskId} AND deleted = 0 AND state IN ('QUEUED','LEASED')
               AND version = #{version}
            """)
    int expireBeforeDispatch(@Param("taskId") String taskId, @Param("version") long version,
                             @Param("now") LocalDateTime now);

    @Update("""
            UPDATE ai_call_task
               SET lease_expiry = #{leaseExpiry}, version = version + 1, update_time = #{now}
             WHERE task_id = #{taskId} AND deleted = 0 AND state = 'RUNNING' AND lease_owner = #{owner}
            """)
    int renewRunningLease(@Param("taskId") String taskId, @Param("owner") String owner,
                          @Param("leaseExpiry") LocalDateTime leaseExpiry, @Param("now") LocalDateTime now);

    @Update("""
            UPDATE ai_call_task
               SET state = #{terminalState}, result_payload = #{resultPayload}, error_code = #{errorCode},
                   request_payload = '', finished_at = #{now}, lease_expiry = NULL,
                   version = version + 1, update_time = #{now}
             WHERE task_id = #{taskId} AND deleted = 0 AND state = 'RUNNING' AND lease_owner = #{owner}
            """)
    int completeRunning(@Param("taskId") String taskId, @Param("owner") String owner,
                        @Param("terminalState") String terminalState,
                        @Param("resultPayload") String resultPayload, @Param("errorCode") String errorCode,
                        @Param("now") LocalDateTime now);

    @Update("""
            UPDATE ai_call_task
               SET cancel_requested = 1,
                   state = CASE WHEN state IN ('QUEUED','LEASED') THEN 'CANCELLED' ELSE state END,
                   finished_at = CASE WHEN state IN ('QUEUED','LEASED') THEN #{now} ELSE finished_at END,
                   version = version + 1, update_time = #{now}
             WHERE task_id = #{taskId} AND deleted = 0
               AND state IN ('QUEUED','LEASED','RUNNING')
            """)
    int requestCancel(@Param("taskId") String taskId, @Param("now") LocalDateTime now);
}
