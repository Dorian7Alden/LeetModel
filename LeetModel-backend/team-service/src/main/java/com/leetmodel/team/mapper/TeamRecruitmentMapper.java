package com.leetmodel.team.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.leetmodel.team.entity.TeamRecruitment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface TeamRecruitmentMapper extends BaseMapper<TeamRecruitment> {
    @Select("SELECT * FROM team_recruitment WHERE id = #{id} FOR UPDATE")
    TeamRecruitment selectByIdForUpdate(@Param("id") Long id);
}
