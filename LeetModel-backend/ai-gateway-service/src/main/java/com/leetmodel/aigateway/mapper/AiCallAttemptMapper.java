package com.leetmodel.aigateway.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.leetmodel.aigateway.entity.AiCallAttempt;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface AiCallAttemptMapper extends BaseMapper<AiCallAttempt> {

    @Select("""
            SELECT * FROM ai_call_attempt
             WHERE deleted = 0 AND task_id = #{taskId}
             ORDER BY attempt_no DESC
             LIMIT 1
            """)
    AiCallAttempt selectLatest(@Param("taskId") String taskId);

    @Update("""
            UPDATE ai_call_attempt
               SET state = #{toState}, sent_at = CASE WHEN #{toState} = 'DISPATCHING' THEN #{now} ELSE sent_at END,
                   acknowledged_at = CASE WHEN #{toState} = 'ACKNOWLEDGED' THEN #{now} ELSE acknowledged_at END,
                   finished_at = CASE WHEN #{toState} IN ('SUCCEEDED','FAILED','UNKNOWN') THEN #{now} ELSE finished_at END,
                   error_code = #{errorCode}, update_time = #{now}
             WHERE attempt_id = #{attemptId} AND deleted = 0 AND state = #{fromState}
            """)
    int transition(@Param("attemptId") String attemptId, @Param("fromState") String fromState,
                   @Param("toState") String toState, @Param("errorCode") String errorCode,
                   @Param("now") LocalDateTime now);
}
