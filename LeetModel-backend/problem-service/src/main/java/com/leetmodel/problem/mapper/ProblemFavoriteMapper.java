package com.leetmodel.problem.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.leetmodel.problem.entity.ProblemFavorite;
import org.apache.ibatis.annotations.Mapper;

/**
 * 题目收藏关联 Mapper。
 */
@Mapper
public interface ProblemFavoriteMapper extends BaseMapper<ProblemFavorite> {
}
