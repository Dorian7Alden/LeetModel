package com.leetmodel.common.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 团队远程调用 DTO（精简信息，供其他服务使用）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TeamDTO {

    private Long id;
    private String name;
    private Long leaderId;
    private Integer status;
    private Integer memberCount;
    private Long problemId;
    private String practiceStatus;
    private java.time.LocalDateTime startedAt;
    private java.time.LocalDateTime deadlineAt;
}
