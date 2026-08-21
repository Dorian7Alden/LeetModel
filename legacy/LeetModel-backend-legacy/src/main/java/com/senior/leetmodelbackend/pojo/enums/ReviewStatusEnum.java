package com.senior.leetmodelbackend.pojo.enums;

import lombok.Getter;

@Getter
public enum ReviewStatusEnum {
    PENDING("待评审"),
    RUNNING("评审中"),
    COMPLETED("已完成"),
    FAILED("评审失败");

    private final String description;

    ReviewStatusEnum(String description) {
        this.description = description;
    }
}
