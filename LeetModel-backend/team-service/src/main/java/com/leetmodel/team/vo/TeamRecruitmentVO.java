package com.leetmodel.team.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class TeamRecruitmentVO {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    private Boolean needModeler;
    private Boolean needProgrammer;
    private Boolean needWriter;
    private String description;
    private String status;
    private LocalDateTime createTime;
}
