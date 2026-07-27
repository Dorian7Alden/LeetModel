package com.leetmodel.problem.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.leetmodel.problem.entity.ProblemLink;
import org.apache.ibatis.annotations.Mapper;

/**
 * 题目外部链接 Mapper。
 *
 * @author LeetModel
 */
@Mapper
public interface ProblemLinkMapper extends BaseMapper<ProblemLink> {
}
