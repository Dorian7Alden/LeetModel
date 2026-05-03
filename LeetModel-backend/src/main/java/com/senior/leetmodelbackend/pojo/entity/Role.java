package com.senior.leetmodelbackend.pojo.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class Role {
    private Integer roleId;
    private String name;
    private String code;
    private String description;
    private Boolean status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
