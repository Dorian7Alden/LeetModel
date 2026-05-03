package com.senior.leetmodelbackend.pojo.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class User {

    private Long userId;
    private String username;
    private String email;

    @JsonIgnore
    private String password;

    private String school;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String phone;

    private Long avatarFileId;
    private String status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

}
