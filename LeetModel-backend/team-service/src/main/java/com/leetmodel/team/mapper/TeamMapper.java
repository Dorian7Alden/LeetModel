package com.leetmodel.team.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.leetmodel.team.entity.Team;
import com.leetmodel.team.vo.PopularPracticeProblemVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 团队 Mapper。
 */
@Mapper
public interface TeamMapper extends BaseMapper<Team> {

    /**
     * 查询团队并锁定当前行，串行化成员变更。
     *
     * @param teamId 团队 ID
     * @return 团队实体
     */
    @Select("SELECT * FROM team WHERE id = #{teamId} AND deleted = 0 FOR UPDATE")
    Team selectByIdForUpdate(@Param("teamId") Long teamId);

    /**
     * 按有效队伍数量查询练习次数最多的题目。
     *
     * @param limit 最大候选题目数
     * @return 仅包含题目标识和练习次数的聚合结果
     */
    @Select("""
            SELECT problem_id AS problemId, COUNT(*) AS practiceCount
            FROM team
            WHERE status = 1
              AND deleted = 0
              AND problem_id > 0
            GROUP BY problem_id
            ORDER BY practiceCount DESC, problem_id DESC
            LIMIT #{limit}
            """)
    List<PopularPracticeProblemVO> selectPopularPracticeProblems(@Param("limit") int limit);
}
