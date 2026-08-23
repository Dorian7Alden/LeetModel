package com.leetmodel.team.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 入队申请视图。
 */
@Data
@Builder
public class JoinApplicationVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long teamId;
    private Long applicantId;
    private String nickname;
    private String avatarUrl;
    private Boolean desiredModeler;
    private Boolean desiredProgrammer;
    private Boolean desiredWriter;
    private String message;
    private String status;
    private Long handledBy;
    private LocalDateTime handledAt;
    private LocalDateTime createTime;
}
