package com.senior.leetmodelbackend.pojo.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class LoginVO {

    private String token;

    private Integer id;
    private String email;
    private String username;
    private String role;
    private String school;
    private String trainerType;
    private String status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
