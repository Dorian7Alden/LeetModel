package com.senior.leetmodelbackend.pojo.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class Problem {
    private Integer problemId;
    private String problemTitle;
    private Integer contentFileId;
    private BigDecimal aveScore;
    private Integer problemStatus;
    private Integer creatorId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
