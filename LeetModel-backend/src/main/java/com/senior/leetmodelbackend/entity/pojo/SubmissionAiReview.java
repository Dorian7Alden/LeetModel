package com.senior.leetmodelbackend.entity.pojo;

import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SubmissionAiReview {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    Integer id;
    Integer userId;
    Integer problemId;
    String status;
    String submissionContent;
    String aiFeedback;
    Integer aiScore;
    LocalDateTime createTime;
    LocalDateTime updateTime;

}
