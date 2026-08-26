package com.leetmodel.assistant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.leetmodel.assistant.entity.AssistantMessage;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

public interface AssistantMessageMapper extends BaseMapper<AssistantMessage> {

    @Update("UPDATE assistant_message SET status = 'RETRYING', error_message = NULL, "
            + "update_time = #{now} WHERE id = #{id} AND role = 'ASSISTANT' "
            + "AND status = 'FAILED' AND deleted = 0")
    int claimRetry(@Param("id") Long id, @Param("now") LocalDateTime now);

    @Update("UPDATE assistant_message SET status = 'FAILED', "
            + "error_message = '回复生成中断，请再次重试', update_time = #{now} "
            + "WHERE role = 'ASSISTANT' AND status IN ('PROCESSING', 'RETRYING') "
            + "AND update_time < #{cutoff} AND deleted = 0")
    int recoverStaleRetries(@Param("cutoff") LocalDateTime cutoff, @Param("now") LocalDateTime now);

    @Update("UPDATE assistant_message SET status = 'COMPLETED', content = #{content}, "
            + "error_message = NULL, tool_context_json = #{toolContextJson}, model_name = #{modelName}, "
            + "ai_call_id = #{aiCallId}, update_time = #{now} WHERE id = #{id} AND deleted = 0")
    int complete(@Param("id") Long id, @Param("content") String content,
                 @Param("toolContextJson") String toolContextJson,
                 @Param("modelName") String modelName, @Param("aiCallId") String aiCallId,
                 @Param("now") LocalDateTime now);

    @Update("UPDATE assistant_message SET status = 'FAILED', content = NULL, "
            + "error_message = #{errorMessage}, tool_context_json = #{toolContextJson}, "
            + "model_name = NULL, ai_call_id = NULL, update_time = #{now} "
            + "WHERE id = #{id} AND deleted = 0")
    int fail(@Param("id") Long id, @Param("errorMessage") String errorMessage,
             @Param("toolContextJson") String toolContextJson, @Param("now") LocalDateTime now);
}
