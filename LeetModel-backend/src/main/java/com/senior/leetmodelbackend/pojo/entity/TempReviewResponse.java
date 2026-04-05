package com.senior.leetmodelbackend.pojo.entity;

import lombok.Data;

@Data
public class TempReviewResponse {

    private Integer id;
    private Double totalScore;
    private String dimension1Name;
    private String dimension2Name;
    private String dimension3Name;
    private String dimension4Name;
    private String dimension5Name;
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

}
