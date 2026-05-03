package com.senior.leetmodelbackend.pojo.dto;

import lombok.Data;

@Data
public class UserUpdateDTO {
    private String username;
    private String school;
    private Integer avatarFileId;
}
