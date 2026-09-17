package com.leetmodel.evaluation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.leetmodel.evaluation.entity.EvaluationWeightScheme;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

public interface EvaluationWeightSchemeMapper extends BaseMapper<EvaluationWeightScheme> {

    @Update("UPDATE evaluation_weight_scheme SET status = 'INACTIVE', "
            + "deactivated_by = #{operatorId}, deactivated_at = #{now}, update_time = #{now} "
            + "WHERE id = #{id} AND status = 'ACTIVE' AND deleted = 0")
    int deactivate(@Param("id") Long id,
                   @Param("operatorId") Long operatorId,
                   @Param("now") LocalDateTime now);
}
