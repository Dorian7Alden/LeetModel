package com.leetmodel.evaluation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.leetmodel.evaluation.entity.EvaluationTask;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public interface EvaluationTaskMapper extends BaseMapper<EvaluationTask> {

    @Update("UPDATE evaluation_task SET status = 'RUNNING', started_at = COALESCE(started_at, #{now}), "
            + "update_time = #{now} WHERE id = #{id} AND status IN ('WAITING', 'RUNNING') AND deleted = 0")
    int markRunning(@Param("id") Long id, @Param("now") LocalDateTime now);

    @Update("UPDATE evaluation_task SET status = 'WAITING', terminal_slots = 0, failed_slots = 0, "
            + "environment_failures = 0, validity_score = NULL, stability_score = NULL, "
            + "success_rate = NULL, latency_score = NULL, overall_score = NULL, avg_duration_ms = NULL, "
            + "retry_count = retry_count + 1, error_message = NULL, started_at = NULL, finished_at = NULL, "
            + "update_time = #{now} WHERE id = #{id} AND status = 'FAILED' AND deleted = 0")
    int resetForRetry(@Param("id") Long id, @Param("now") LocalDateTime now);

    @Update("UPDATE evaluation_task SET status = #{status}, terminal_slots = #{terminalSlots}, "
            + "failed_slots = #{failedSlots}, environment_failures = #{environmentFailures}, "
            + "error_message = #{errorMessage}, update_time = #{now} WHERE id = #{id} AND deleted = 0")
    int updateProgress(@Param("id") Long id, @Param("status") String status,
                       @Param("terminalSlots") Integer terminalSlots,
                       @Param("failedSlots") Integer failedSlots,
                       @Param("environmentFailures") Integer environmentFailures,
                       @Param("errorMessage") String errorMessage,
                       @Param("now") LocalDateTime now);

    @Update("UPDATE evaluation_task SET status = 'COMPLETED', terminal_slots = #{terminalSlots}, "
            + "failed_slots = #{failedSlots}, environment_failures = 0, validity_score = #{validityScore}, "
            + "stability_score = #{stabilityScore}, success_rate = #{successRate}, "
            + "latency_score = #{latencyScore}, overall_score = #{overallScore}, "
            + "avg_duration_ms = #{avgDurationMs}, error_message = NULL, finished_at = #{now}, "
            + "update_time = #{now} WHERE id = #{id} AND deleted = 0")
    int complete(@Param("id") Long id, @Param("terminalSlots") Integer terminalSlots,
                 @Param("failedSlots") Integer failedSlots,
                 @Param("validityScore") BigDecimal validityScore,
                 @Param("stabilityScore") BigDecimal stabilityScore,
                 @Param("successRate") BigDecimal successRate,
                 @Param("latencyScore") BigDecimal latencyScore,
                 @Param("overallScore") BigDecimal overallScore,
                 @Param("avgDurationMs") Long avgDurationMs,
                 @Param("now") LocalDateTime now);

    @Update("UPDATE evaluation_task SET status = 'FAILED', terminal_slots = #{terminalSlots}, "
            + "failed_slots = #{failedSlots}, environment_failures = #{environmentFailures}, "
            + "validity_score = NULL, stability_score = NULL, success_rate = NULL, "
            + "latency_score = NULL, overall_score = NULL, avg_duration_ms = NULL, "
            + "error_message = #{errorMessage}, finished_at = #{now}, update_time = #{now} "
            + "WHERE id = #{id} AND deleted = 0")
    int fail(@Param("id") Long id, @Param("terminalSlots") Integer terminalSlots,
             @Param("failedSlots") Integer failedSlots,
             @Param("environmentFailures") Integer environmentFailures,
             @Param("errorMessage") String errorMessage,
             @Param("now") LocalDateTime now);
}
