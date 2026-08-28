package com.leetmodel.aigateway.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.leetmodel.aigateway.entity.AiCallTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface AiCallTaskMapper extends BaseMapper<AiCallTask> {

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
}
