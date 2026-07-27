package com.leetmodel.problem.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.leetmodel.problem.entity.Problem;
import org.apache.ibatis.annotations.Mapper;

/**
 * 题目 Mapper。
 *
 * @author LeetModel
 */
@Mapper
public interface ProblemMapper extends BaseMapper<Problem> {
}
