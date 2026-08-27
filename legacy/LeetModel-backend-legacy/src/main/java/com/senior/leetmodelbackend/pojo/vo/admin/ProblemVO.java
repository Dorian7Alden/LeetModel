package com.senior.leetmodelbackend.pojo.vo.admin;

import com.senior.leetmodelbackend.pojo.entity.Problem;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class ProblemVO {

    private Integer problemId;
    private String problemTitle;
    private Integer contentFileId;
    private String contentFileUrl;
    private BigDecimal aveScore;
    private Integer problemStatus;
    private Integer creatorId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public static ProblemVO createVO(Problem problem) {
        ProblemVO vo = new ProblemVO();
        vo.setProblemId(problem.getProblemId());
        vo.setProblemTitle(problem.getProblemTitle());
        vo.setContentFileId(problem.getContentFileId());
        vo.setAveScore(problem.getAveScore());
        vo.setProblemStatus(problem.getProblemStatus());
        vo.setCreatorId(problem.getCreatorId());
        vo.setCreateTime(problem.getCreateTime());
        vo.setUpdateTime(problem.getUpdateTime());
        return vo;
    }
}
