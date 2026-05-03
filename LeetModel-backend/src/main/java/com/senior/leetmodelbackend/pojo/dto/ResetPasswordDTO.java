package com.senior.leetmodelbackend.pojo.dto;

import lombok.Data;

@Data
public class ResetPasswordDTO {

    private String email;
    private String password;
    private String code;

}
