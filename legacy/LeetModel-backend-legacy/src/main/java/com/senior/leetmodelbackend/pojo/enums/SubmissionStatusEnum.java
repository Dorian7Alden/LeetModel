package com.senior.leetmodelbackend.pojo.enums;

import lombok.Getter;

@Getter
public enum SubmissionStatusEnum {
    PENDING("待审核"),
    EVALUATING("评审中"),
    COMPLETED("已完成"),
    FAILED("评审失败");

    private final String description;

    SubmissionStatusEnum(String description) {
        this.description = description;
    }
}
