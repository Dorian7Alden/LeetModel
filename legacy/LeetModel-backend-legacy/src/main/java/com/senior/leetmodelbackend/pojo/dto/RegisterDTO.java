package com.senior.leetmodelbackend.pojo.dto;

import lombok.Data;

@Data
public class RegisterDTO {
    private String email;
    private String password;
    private String code;
}
