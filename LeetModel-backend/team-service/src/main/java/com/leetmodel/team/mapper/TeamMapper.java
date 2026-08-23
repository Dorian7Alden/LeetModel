package com.leetmodel.team.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.leetmodel.team.entity.Team;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

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
}
