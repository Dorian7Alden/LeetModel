package com.senior.leetmodelbackend.pojo.vo.admin;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ReviewVO {
    private Integer reviewId;
    private String dimensionCode;
    private String dimensionName;
    private BigDecimal score;
    private BigDecimal weight;
    private String feedback;
    private String status;

    public static ReviewVO from(com.senior.leetmodelbackend.pojo.entity.Review r) {
        ReviewVO vo = new ReviewVO();
        vo.setReviewId(r.getReviewId());
        vo.setDimensionCode(r.getDimensionCode());
        vo.setDimensionName(r.getDimensionName());
        vo.setScore(r.getScore());
        vo.setWeight(r.getWeight());
        vo.setFeedback(r.getFeedback());
        vo.setStatus(r.getStatus());
        return vo;
    }
}
