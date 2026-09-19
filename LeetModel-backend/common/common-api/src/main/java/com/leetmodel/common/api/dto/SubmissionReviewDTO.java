package com.leetmodel.common.api.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
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
    @JsonSerialize(using = ToStringSerializer.class)
    private Long fileId;
}
