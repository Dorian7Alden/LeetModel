package com.senior.leetmodelbackend.entity.vo;

import com.senior.leetmodelbackend.entity.pojo.User;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class LoginVO {

    private String token;

    private Integer userId;
    private String email;
    private String name;
    private String role;
    private String school;
    private String trainerType;
    private String status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
