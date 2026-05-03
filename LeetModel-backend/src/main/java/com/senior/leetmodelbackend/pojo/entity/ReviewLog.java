package com.senior.leetmodelbackend.pojo.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ReviewLog {
    private Integer logId;
    private Integer submissionId;
    private Integer reviewId;
    private String status;
    private String message;
    private String detail;
    private LocalDateTime createTime;
}
