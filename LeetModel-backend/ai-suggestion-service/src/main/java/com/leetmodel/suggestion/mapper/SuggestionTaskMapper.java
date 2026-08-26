package com.leetmodel.suggestion.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.leetmodel.suggestion.entity.SuggestionTask;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

public interface SuggestionTaskMapper extends BaseMapper<SuggestionTask> {

    @Select("SELECT * FROM suggestion_task WHERE status = 'WAITING' "
            + "AND next_run_at <= #{now} AND deleted = 0 ORDER BY next_run_at, id LIMIT 1")
    SuggestionTask selectNextWaiting(@Param("now") LocalDateTime now);

    @Update("UPDATE suggestion_task SET status = 'RUNNING', started_at = #{now}, "
            + "update_time = #{now} WHERE id = #{id} AND status = 'WAITING' AND deleted = 0")
    int claim(@Param("id") Long id, @Param("now") LocalDateTime now);

    @Update("UPDATE suggestion_task SET status = 'WAITING', retry_count = retry_count + 1, "
            + "next_run_at = #{now}, started_at = NULL, finished_at = NULL, error_message = NULL, "
            + "result_json = NULL, model_name = NULL, ai_call_id = NULL, update_time = #{now} "
            + "WHERE id = #{id} AND status = 'FAILED' AND deleted = 0")
    int resetForRetry(@Param("id") Long id, @Param("now") LocalDateTime now);

    @Update("UPDATE suggestion_task SET status = 'WAITING', retry_count = retry_count + 1, "
            + "next_run_at = #{now}, started_at = NULL, error_message = '任务中断，已自动恢复', "
            + "update_time = #{now} WHERE status = 'RUNNING' AND started_at < #{cutoff} AND deleted = 0")
    int recoverStale(@Param("cutoff") LocalDateTime cutoff, @Param("now") LocalDateTime now);
}
