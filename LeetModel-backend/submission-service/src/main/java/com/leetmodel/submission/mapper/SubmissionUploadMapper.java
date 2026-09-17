package com.leetmodel.submission.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.leetmodel.submission.entity.SubmissionUpload;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 论文上传会话数据访问。
 */
@Mapper
public interface SubmissionUploadMapper extends BaseMapper<SubmissionUpload> {

    @Select("SELECT * FROM submission_upload WHERE upload_token = #{uploadToken} AND deleted = 0 LIMIT 1")
    SubmissionUpload selectByToken(@Param("uploadToken") String uploadToken);

    @Select("SELECT * FROM submission_upload WHERE team_id = #{teamId} AND active_marker = 1 AND deleted = 0 LIMIT 1")
    SubmissionUpload selectActiveByTeamId(@Param("teamId") Long teamId);

    @Select("SELECT * FROM submission_upload WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    SubmissionUpload selectForUpdate(@Param("id") Long id);

    @Select("""
            SELECT * FROM submission_upload
             WHERE deleted = 0
               AND active_marker = 1
               AND expires_at <= #{now}
               AND submission_id IS NULL
               AND (status = 'UPLOADING'
                    OR (status = 'COMPLETING' AND completing_at < #{staleBefore}))
            """)
    List<SubmissionUpload> selectExpired(@Param("now") LocalDateTime now,
                                         @Param("staleBefore") LocalDateTime staleBefore);

    @Update("""
            UPDATE submission_upload
               SET status = 'COMPLETING', completing_at = #{now}, update_time = #{now}
             WHERE id = #{id}
               AND deleted = 0
               AND submission_id IS NULL
               AND (status = 'UPLOADING'
                    OR (status = 'COMPLETING' AND completing_at < #{staleBefore}))
            """)
    int claimCompletion(@Param("id") Long id,
                        @Param("now") LocalDateTime now,
                        @Param("staleBefore") LocalDateTime staleBefore);

    @Update("""
            UPDATE submission_upload
               SET status = 'UPLOADING', completing_at = NULL, update_time = CURRENT_TIMESTAMP
             WHERE id = #{id} AND status = 'COMPLETING' AND submission_id IS NULL AND deleted = 0
            """)
    int resetCompletion(@Param("id") Long id);

    @Update("""
            UPDATE submission_upload
               SET submission_id = #{submissionId}, update_time = CURRENT_TIMESTAMP
             WHERE id = #{id} AND submission_id IS NULL AND deleted = 0
            """)
    int linkSubmission(@Param("id") Long id, @Param("submissionId") Long submissionId);

    @Update("""
            UPDATE submission_upload
               SET status = 'COMPLETED', active_marker = NULL, completing_at = NULL,
                   submission_id = #{submissionId}, update_time = CURRENT_TIMESTAMP
             WHERE id = #{id} AND deleted = 0
            """)
    int markCompleted(@Param("id") Long id, @Param("submissionId") Long submissionId);

    @Update("""
            UPDATE submission_upload
               SET status = #{status}, active_marker = NULL, completing_at = NULL,
                   update_time = CURRENT_TIMESTAMP
             WHERE id = #{id} AND active_marker = 1 AND submission_id IS NULL AND deleted = 0
               AND status = #{expectedStatus}
            """)
    int markTerminal(@Param("id") Long id,
                     @Param("expectedStatus") String expectedStatus,
                     @Param("status") String status);
}
