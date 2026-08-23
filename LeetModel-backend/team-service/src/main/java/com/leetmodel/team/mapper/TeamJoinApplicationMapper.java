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
}
