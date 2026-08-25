package com.leetmodel.common.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 队伍作品提交资格摘要。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TeamSubmissionAccessDTO {

    private Long teamId;
    private Long problemId;
    private Boolean member;
    private Boolean canSubmit;
    private String practiceStatus;
    private LocalDateTime deadlineAt;
    private LocalDateTime endedAt;
}
