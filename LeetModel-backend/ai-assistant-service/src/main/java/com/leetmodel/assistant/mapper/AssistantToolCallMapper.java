package com.leetmodel.assistant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.leetmodel.assistant.entity.AssistantToolCall;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/** 客服工具调用状态的条件更新入口。 */
public interface AssistantToolCallMapper extends BaseMapper<AssistantToolCall> {

    @Update("UPDATE assistant_tool_call SET status = 'RUNNING', arguments_json = #{argumentsJson}, "
            + "started_at = #{startedAt}, update_time = #{startedAt} "
            + "WHERE id = #{id} AND status = 'RECEIVED' AND deleted = 0")
    int markRunning(@Param("id") Long id, @Param("argumentsJson") String argumentsJson,
                    @Param("startedAt") LocalDateTime startedAt);

    @Update("UPDATE assistant_tool_call SET status = 'REJECTED', error_code = #{errorCode}, "
            + "finished_at = #{finishedAt}, update_time = #{finishedAt} "
            + "WHERE id = #{id} AND status = 'RECEIVED' AND deleted = 0")
    int reject(@Param("id") Long id, @Param("errorCode") String errorCode,
               @Param("finishedAt") LocalDateTime finishedAt);

    @Update("UPDATE assistant_tool_call SET status = 'COMPLETED', "
            + "result_snapshot_json = #{resultSnapshotJson}, nested_ai_call_id = #{nestedAiCallId}, "
            + "duration_ms = #{durationMs}, finished_at = #{finishedAt}, update_time = #{finishedAt} "
            + "WHERE id = #{id} AND status = 'RUNNING' AND deleted = 0")
    int complete(@Param("id") Long id, @Param("resultSnapshotJson") String resultSnapshotJson,
                 @Param("nestedAiCallId") String nestedAiCallId,
                 @Param("durationMs") Long durationMs,
                 @Param("finishedAt") LocalDateTime finishedAt);

    @Update("UPDATE assistant_tool_call SET status = #{status}, error_code = #{errorCode}, "
            + "duration_ms = #{durationMs}, finished_at = #{finishedAt}, update_time = #{finishedAt} "
            + "WHERE id = #{id} AND status = 'RUNNING' AND deleted = 0")
    int fail(@Param("id") Long id, @Param("status") String status,
             @Param("errorCode") String errorCode, @Param("durationMs") Long durationMs,
             @Param("finishedAt") LocalDateTime finishedAt);

}
