package com.leetmodel.team.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.leetmodel.team.entity.Team;
import com.leetmodel.team.vo.PopularPracticeProblemVO;
import com.leetmodel.team.vo.ProblemParticipationStatsVO;
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

    /**
     * 聚合指定题目的有效队伍与当前成员数量。
     *
     * @param problemId 题目标识
     * @return 参赛统计
     */
    @Select("""
            SELECT COUNT(DISTINCT t.id) AS teamCount,
                   COUNT(DISTINCT tm.user_id) AS participantCount
            FROM team t
            LEFT JOIN team_member tm ON tm.team_id = t.id
            WHERE t.problem_id = #{problemId}
              AND t.status = 1
              AND t.deleted = 0
            """)
    ProblemParticipationStatsVO selectProblemParticipationStats(@Param("problemId") Long problemId);
}
