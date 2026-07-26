package com.senior.leetmodelbackend.mapper;

import com.senior.leetmodelbackend.pojo.entity.Review;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ReviewMapper {

    List<Review> getReviewsBySubmissionId(@Param("submissionId") Integer submissionId);

    void insertReview(Review review);

    void updateReviewResult(@Param("reviewId") Integer reviewId,
                            @Param("score") java.math.BigDecimal score,
                            @Param("feedback") String feedback,
                            @Param("status") String status);

    void updateReviewStatus(@Param("reviewId") Integer reviewId,
                            @Param("status") String status,
                            @Param("retryCount") Integer retryCount);

    void updateReviewStart(@Param("reviewId") Integer reviewId,
                           @Param("status") String status);
}
