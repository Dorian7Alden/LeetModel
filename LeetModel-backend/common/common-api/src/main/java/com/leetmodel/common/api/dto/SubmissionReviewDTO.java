package com.leetmodel.common.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SubmissionReviewDTO {
    private Long id;
    private Long teamId;
    private Long problemId;
    private Integer version;
    private String objectName;
}
