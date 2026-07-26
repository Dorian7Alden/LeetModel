package com.senior.leetmodelbackend.pojo.dto.admin;

import lombok.Data;

@Data
public class SubmissionDTO {
    private Integer problemId;
    private String title;
    private Integer contentFileId;
}
