package com.senior.leetmodelbackend.pojo.dto.admin;

import lombok.Data;

@Data
public class ProblemDTO {
    private String problemTitle;
    private Integer contentFileId;
    private Integer problemStatus;
}
