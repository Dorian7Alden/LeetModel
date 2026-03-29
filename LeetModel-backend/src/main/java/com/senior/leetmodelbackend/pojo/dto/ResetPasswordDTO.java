package com.senior.leetmodelbackend.pojo.dto;

import lombok.Data;

@Data
public class ResetPasswordDTO {

    String email;
    String password;
    String code;

}
