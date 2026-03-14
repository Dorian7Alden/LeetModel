package com.senior.leetmodelbackend.pojo;


import lombok.Data;

import java.time.LocalDateTime;

@Data
public class User {

    private int id;
    private String username;
    private String email;
    private String password;
    private String school;
    private String phone;
    private String role;
    private String trainerType;
    private String status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

}
