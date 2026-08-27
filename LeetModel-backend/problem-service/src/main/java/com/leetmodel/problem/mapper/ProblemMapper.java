package com.leetmodel.problem.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.leetmodel.problem.entity.Problem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * 题目 Mapper。
 */
@Mapper
public interface ProblemMapper extends BaseMapper<Problem> {

    /**
     * 查询全部题目（含逻辑删除）的最大题号。
     * 题号唯一索引覆盖所有行，需要避开逻辑删除过滤。
     */
    @Select("SELECT COALESCE(MAX(code), 1000) FROM problem")
    Integer selectMaxCode();
}
