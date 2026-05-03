package com.senior.leetmodelbackend.pojo.dto.admin;

import lombok.Data;

import java.util.List;

@Data
public class UserBatchUpdateDTO {
    private List<Integer> userIds;
    private String school;
    private String phone;
    private String status;
}
