package com.senior.leetmodelbackend.pojo.entity;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "problem_review_dimension")
public class ProblemReviewDimension {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long reportId;

    private Integer dimensionIndex;

    private String dimensionName;

    private Double weight;

    private Integer dimensionScore;

    @Column(columnDefinition = "TEXT")
    private String scoringReason;
}
