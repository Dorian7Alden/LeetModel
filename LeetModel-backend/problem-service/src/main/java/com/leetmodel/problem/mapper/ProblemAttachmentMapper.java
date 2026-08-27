package com.leetmodel.problem.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.leetmodel.problem.entity.ProblemAttachment;
import org.apache.ibatis.annotations.Mapper;

/**
 * 题目附件 Mapper。
 */
@Mapper
public interface ProblemAttachmentMapper extends BaseMapper<ProblemAttachment> {
}
