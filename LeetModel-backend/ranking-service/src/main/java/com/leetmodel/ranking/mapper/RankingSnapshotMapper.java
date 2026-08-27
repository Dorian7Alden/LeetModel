package com.leetmodel.ranking.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.leetmodel.ranking.entity.RankingSnapshot;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface RankingSnapshotMapper extends BaseMapper<RankingSnapshot> {

    @Update("UPDATE ranking_snapshot SET current_marker = NULL "
            + "WHERE problem_id = #{problemId} AND current_marker = 1 AND deleted = 0")
    int deactivateCurrent(@Param("problemId") Long problemId);
}
