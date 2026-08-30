package com.leetmodel.assistant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.leetmodel.assistant.entity.AssistantProductionPointer;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

public interface AssistantProductionPointerMapper extends BaseMapper<AssistantProductionPointer> {

    @Select("SELECT * FROM assistant_production_pointer WHERE id = #{id} FOR UPDATE")
    AssistantProductionPointer selectByIdForUpdate(@Param("id") Long id);

    @Update("UPDATE assistant_production_pointer SET active_config_id = #{targetConfigId}, "
            + "revision = revision + 1, activated_by = #{operatorId}, activated_at = #{now}, "
            + "observation_until = #{observationUntil}, update_time = #{now} "
            + "WHERE id = 1 AND revision = #{expectedRevision} "
            + "AND active_config_id = #{sourceConfigId}")
    int activate(@Param("sourceConfigId") Long sourceConfigId,
                 @Param("targetConfigId") Long targetConfigId,
                 @Param("expectedRevision") Long expectedRevision,
                 @Param("operatorId") Long operatorId,
                 @Param("now") LocalDateTime now,
                 @Param("observationUntil") LocalDateTime observationUntil);
}
