package com.leetmodel.team.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.leetmodel.team.entity.TeamMember;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 团队成员 Mapper。
 */
@Mapper
public interface TeamMemberMapper extends BaseMapper<TeamMember> {

    @Select("SELECT COUNT(*) FROM team_member tm JOIN team t ON t.id = tm.team_id " +
            "WHERE tm.user_id = #{userId} AND t.problem_id = #{problemId} AND t.deleted = 0 " +
            "AND t.status = 1 AND t.practice_status IN ('PREPARING','IN_PROGRESS','SUBMITTED')")
    long countActiveProblemTeams(@Param("userId") Long userId, @Param("problemId") Long problemId);
}
