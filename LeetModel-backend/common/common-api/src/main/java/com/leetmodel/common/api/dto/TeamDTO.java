package com.leetmodel.common.api.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
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

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    private String name;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long leaderId;
    private Integer status;
    private Integer memberCount;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long problemId;
    private String practiceStatus;
    private java.time.LocalDateTime startedAt;
    private java.time.LocalDateTime deadlineAt;
    private java.time.LocalDateTime endedAt;
}
