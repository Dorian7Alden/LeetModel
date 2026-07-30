package com.leetmodel.team.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.leetmodel.team.entity.TeamMember;
import org.apache.ibatis.annotations.Mapper;

/**
 * 团队成员 Mapper。
 *
 * @author LeetModel
 */
@Mapper
public interface TeamMemberMapper extends BaseMapper<TeamMember> {
}
