package com.senior.leetmodelbackend.entity.dto;

import lombok.Data;

@Data
public class ResetPasswordDTO {

    String email;
    String password;
    String code;

}
