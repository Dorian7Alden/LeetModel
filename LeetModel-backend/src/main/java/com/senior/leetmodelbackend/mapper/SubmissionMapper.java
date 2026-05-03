package com.senior.leetmodelbackend.mapper;

import com.senior.leetmodelbackend.pojo.entity.Submission;
import com.senior.leetmodelbackend.pojo.vo.admin.SubmissionVO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SubmissionMapper {

    List<SubmissionVO> getSubmissionList(@Param("keyword") String keyword,
                                         @Param("status") String status);

    Submission getSubmissionById(@Param("submissionId") Integer submissionId);

    void insertSubmission(Submission submission);

    void updateSubmissionStatus(@Param("submissionId") Integer submissionId,
                                @Param("status") String status);

    void updateSubmissionScore(@Param("submissionId") Integer submissionId,
                               @Param("totalScore") java.math.BigDecimal totalScore,
                               @Param("status") String status);

    @Delete("delete from submission where submission_id = #{submissionId}")
    void deleteSubmission(@Param("submissionId") Integer submissionId);
}
