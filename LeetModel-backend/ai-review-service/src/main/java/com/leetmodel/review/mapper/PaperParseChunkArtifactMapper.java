package com.leetmodel.review.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.leetmodel.review.entity.PaperParseChunkArtifact;
import org.apache.ibatis.annotations.Mapper;

/** PDF 解析滑窗中间分块持久化 Mapper 接口。 */
@Mapper
public interface PaperParseChunkArtifactMapper extends BaseMapper<PaperParseChunkArtifact> {
}
