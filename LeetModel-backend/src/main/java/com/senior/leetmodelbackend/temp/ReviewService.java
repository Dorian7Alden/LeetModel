package com.senior.leetmodelbackend.temp;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ReviewService {
    @Autowired
    private ReviewMapper reviewMapper;


    public List<Review> getReviews() {
        return reviewMapper.findAll();
    }

    public void insertReview(Review review) {
        reviewMapper.insertReview(review);
    }

    /**
     * 更新审核状态
     * @param id
     * @param status: 0: 未审核 1: 审核中 2: 审核结束
     */
    public void updateStatus(Integer id, Integer status) {
        reviewMapper.updateStatus(id, status);
    }

}
