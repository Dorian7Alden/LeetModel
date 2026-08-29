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

    @Select("SELECT * FROM evaluation_run_attempt WHERE status = 'WAITING' AND deleted = 0 "
            + "ORDER BY create_time ASC, id ASC LIMIT 1")
    EvaluationRunAttempt selectNextWaiting();

    @Update("UPDATE evaluation_run_attempt SET status = 'RUNNING', started_at = #{now}, "
            + "update_time = #{now} WHERE id = #{id} AND status = 'WAITING' AND deleted = 0")
    int claim(@Param("id") Long id, @Param("now") LocalDateTime now);

    @Update("UPDATE evaluation_run_attempt SET status = 'SUCCEEDED', failure_type = NULL, "
            + "score = #{score}, result_json = #{resultJson}, metrics_json = #{metricsJson}, "
            + "model_name = #{modelName}, model_execution_config_version = #{modelConfigVersion}, "
            + "rag_index_version = #{ragIndexVersion}, "
            + "ai_call_id = #{aiCallId}, duration_ms = #{durationMs}, error_message = NULL, "
            + "finished_at = #{now}, update_time = #{now} "
            + "WHERE id = #{id} AND status = 'RUNNING' AND deleted = 0")
    int succeed(@Param("id") Long id, @Param("score") BigDecimal score,
                @Param("resultJson") String resultJson, @Param("metricsJson") String metricsJson,
                @Param("modelName") String modelName,
                @Param("modelConfigVersion") String modelConfigVersion,
                @Param("ragIndexVersion") String ragIndexVersion,
                @Param("aiCallId") String aiCallId, @Param("durationMs") Long durationMs,
                @Param("now") LocalDateTime now);

    @Update("UPDATE evaluation_run_attempt SET status = 'FAILED', failure_type = #{failureType}, "
            + "score = NULL, result_json = NULL, metrics_json = NULL, model_name = NULL, ai_call_id = NULL, "
            + "duration_ms = #{durationMs}, error_message = #{errorMessage}, finished_at = #{now}, "
            + "update_time = #{now} WHERE id = #{id} AND status = 'RUNNING' AND deleted = 0")
    int fail(@Param("id") Long id, @Param("failureType") String failureType,
             @Param("durationMs") Long durationMs, @Param("errorMessage") String errorMessage,
             @Param("now") LocalDateTime now);

    @Select("SELECT * FROM evaluation_run_attempt WHERE status = 'RUNNING' "
            + "AND update_time < #{cutoff} AND deleted = 0 ORDER BY update_time ASC")
    List<EvaluationRunAttempt> selectStale(@Param("cutoff") LocalDateTime cutoff);

    @Update("UPDATE evaluation_run_attempt SET status = 'FAILED', failure_type = 'ENVIRONMENT', "
            + "error_message = '评价运行中断，已创建恢复尝试', finished_at = #{now}, update_time = #{now} "
            + "WHERE id = #{id} AND status = 'RUNNING' AND update_time < #{cutoff} AND deleted = 0")
    int failStale(@Param("id") Long id, @Param("cutoff") LocalDateTime cutoff,
                  @Param("now") LocalDateTime now);
}
