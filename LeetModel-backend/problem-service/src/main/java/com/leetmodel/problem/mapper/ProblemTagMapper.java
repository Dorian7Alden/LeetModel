package com.leetmodel.problem.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.leetmodel.problem.entity.ProblemTag;
import org.apache.ibatis.annotations.Mapper;

/**
 * 题目-标签关联 Mapper。
 *
 * @author LeetModel
 */
@Mapper
public interface ProblemTagMapper extends BaseMapper<ProblemTag> {
}
