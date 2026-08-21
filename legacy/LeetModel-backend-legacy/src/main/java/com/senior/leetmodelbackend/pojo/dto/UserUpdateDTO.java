package com.senior.leetmodelbackend.pojo.dto;

import lombok.Data;

@Data
public class UserUpdateDTO {
    private String username;
    private String school;
    private String phone;
    private String status;
    private Integer avatarFileId;
}
