package com.senior.leetmodelbackend.pojo.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class Submission {
    private Integer submissionId;
    private Integer problemId;
    private Integer userId;
    private String title;
    private Integer contentFileId;
    private String status;
    private BigDecimal totalScore;
    private LocalDateTime submitTime;
    private LocalDateTime completeTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
