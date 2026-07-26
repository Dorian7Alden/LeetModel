package com.senior.leetmodelbackend.pojo.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class Review {
    private Integer reviewId;
    private Integer submissionId;
    private String dimensionCode;
    private String dimensionName;
    private BigDecimal score;
    private BigDecimal weight;
    private String feedback;
    private String status;
    private Integer retryCount;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
