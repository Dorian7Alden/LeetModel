package com.senior.leetmodelbackend.pojo.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Entity
@Table(name = "problem_review_report")
public class ProblemReviewReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String modelingProblemTitle;

    private LocalDate scoringDate;

    private Integer maxScore;

    private Double weightedTotalScore;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @Transient // 不映射到主表字段，仅用于业务关联
    private List<ProblemReviewDimension> dimensions;
}
