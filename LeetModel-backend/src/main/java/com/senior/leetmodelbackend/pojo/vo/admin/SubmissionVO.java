package com.senior.leetmodelbackend.pojo.vo.admin;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class SubmissionVO {
    private Integer submissionId;
    private Integer problemId;
    private String problemTitle;
    private Integer userId;
    private String username;
    private String title;
    private String status;
    private BigDecimal totalScore;
    private LocalDateTime submitTime;
    private LocalDateTime completeTime;
    private List<ReviewVO> reviews;

    public static SubmissionVO from(com.senior.leetmodelbackend.pojo.entity.Submission s) {
        SubmissionVO vo = new SubmissionVO();
        vo.setSubmissionId(s.getSubmissionId());
        vo.setProblemId(s.getProblemId());
        vo.setUserId(s.getUserId());
        vo.setTitle(s.getTitle());
        vo.setStatus(s.getStatus());
        vo.setTotalScore(s.getTotalScore());
        vo.setSubmitTime(s.getSubmitTime());
        vo.setCompleteTime(s.getCompleteTime());
        return vo;
    }
}
