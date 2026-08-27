package com.leetmodel.team.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("team_recruitment")
public class TeamRecruitment implements Serializable {
    private Long id;
    private Long teamId;
    private Boolean needModeler;
    private Boolean needProgrammer;
    private Boolean needWriter;
    private String description;
    private String status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
