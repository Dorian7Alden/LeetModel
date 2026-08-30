package com.leetmodel.submission.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.leetmodel.submission.entity.SubmissionUploadChunk;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 论文上传分片数据访问。
 */
@Mapper
public interface SubmissionUploadChunkMapper extends BaseMapper<SubmissionUploadChunk> {

    @Select("""
            SELECT * FROM submission_upload_chunk
             WHERE upload_id = #{uploadId} AND chunk_index = #{chunkIndex} AND deleted = 0
             LIMIT 1
            """)
    SubmissionUploadChunk selectByUploadAndIndex(@Param("uploadId") Long uploadId,
                                                  @Param("chunkIndex") Integer chunkIndex);

    @Select("""
            SELECT * FROM submission_upload_chunk
             WHERE upload_id = #{uploadId} AND deleted = 0
             ORDER BY chunk_index ASC
            """)
    List<SubmissionUploadChunk> selectByUploadId(@Param("uploadId") Long uploadId);

    @Update("""
            UPDATE submission_upload_chunk
               SET deleted = 1, update_time = CURRENT_TIMESTAMP
             WHERE upload_id = #{uploadId} AND deleted = 0
            """)
    int deleteByUploadId(@Param("uploadId") Long uploadId);
}
