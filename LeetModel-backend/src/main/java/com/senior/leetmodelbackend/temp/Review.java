package com.senior.leetmodelbackend.temp;

import jakarta.persistence.Id;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class Review {

    @Id
    private Integer id;
    private Double totalScore;
    private Integer submissionId;

    private Integer dimension1Score;
    private Integer dimension2Score;
    private Integer dimension3Score;
    private Integer dimension4Score;
    private Integer dimension5Score;

    private Double dimension1Weight;
    private Double dimension2Weight;
    private Double dimension3Weight;
    private Double dimension4Weight;
    private Double dimension5Weight;

    private String dimension1Name;
    private String dimension2Name;
    private String dimension3Name;
    private String dimension4Name;
    private String dimension5Name;

    private String dimension1Review;
    private String dimension2Review;
    private String dimension3Review;
    private String dimension4Review;
    private String dimension5Review;

    private String title;
    private LocalDateTime updateTime;

    /**
     * 0: 未审核
     * 1: 审核中
     * 2: 审核结束
     */
    private Integer status;
}
