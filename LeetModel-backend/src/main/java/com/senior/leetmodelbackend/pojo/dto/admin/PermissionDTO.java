package com.senior.leetmodelbackend.pojo.dto.admin;

import lombok.Data;

@Data
public class PermissionDTO {
    private String name;
    private String code;
    private String description;
    private Boolean status;
}
