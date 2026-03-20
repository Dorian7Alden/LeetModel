package com.senior.leetmodelbackend.mapper;

import com.senior.leetmodelbackend.entity.pojo.SubmissionAiReview;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;

@Mapper
public interface SubmissionAiReviewMapper {

    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(SubmissionAiReview review);

    void update(SubmissionAiReview review);

    SubmissionAiReview selectById(Integer id);
}
