package com.leetmodel.team.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.leetmodel.team.entity.Team;
import org.apache.ibatis.annotations.Mapper;

/**
 * 团队 Mapper。
 *
 * @author LeetModel
 */
@Mapper
public interface TeamMapper extends BaseMapper<Team> {
}
