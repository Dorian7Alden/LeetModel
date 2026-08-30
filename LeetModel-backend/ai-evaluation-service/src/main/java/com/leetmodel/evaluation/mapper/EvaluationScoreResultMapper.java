package com.leetmodel.evaluation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.leetmodel.evaluation.entity.EvaluationScoreResult;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface EvaluationScoreResultMapper extends BaseMapper<EvaluationScoreResult> {

    @Select("SELECT COALESCE(MAX(CAST(SUBSTRING(score_result_version, 15) AS UNSIGNED)), 0) "
            + "FROM evaluation_score_result WHERE task_id = #{taskId} AND deleted = 0 "
            + "AND score_result_version REGEXP '^SCORE_RESULT_V[0-9]+$'")
    int selectMaxVersionNumber(@Param("taskId") Long taskId);
}
