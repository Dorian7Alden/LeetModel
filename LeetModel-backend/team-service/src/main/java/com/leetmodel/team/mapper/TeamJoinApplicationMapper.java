package com.leetmodel.team.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.leetmodel.team.entity.TeamJoinApplication;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 入队申请 Mapper。
 */
@Mapper
public interface TeamJoinApplicationMapper extends BaseMapper<TeamJoinApplication> {

    /**
     * 查询并锁定入队申请。
     *
     * @param applicationId 申请 ID
     * @return 入队申请
     */
    @Select("SELECT * FROM team_join_application WHERE id = #{applicationId} FOR UPDATE")
    TeamJoinApplication selectByIdForUpdate(@Param("applicationId") Long applicationId);

    /**
     * 查询并锁定申请人的待处理申请。
     *
     * @param teamId 队伍 ID
     * @param applicantId 申请人 ID
     * @return 待处理申请
     */
    @Select("SELECT * FROM team_join_application WHERE team_id = #{teamId} " +
            "AND applicant_id = #{applicantId} AND status = 'pending' " +
            "AND pending_marker = 1 FOR UPDATE")
    TeamJoinApplication selectPendingForUpdate(@Param("teamId") Long teamId,
                                               @Param("applicantId") Long applicantId);
}
