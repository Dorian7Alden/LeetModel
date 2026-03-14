package com.senior.leetmodelbackend.pojo;


import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class User {

    private int id;
    private String username;
    private String email;
    @JsonIgnore
    private String password;
    private String school;
    @JsonIgnore
    private String phone; // TODO: 响应数据的时候保护隐私，防止输出完整的手机号
    private String role;
    private String trainerType;
    private String status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

}
