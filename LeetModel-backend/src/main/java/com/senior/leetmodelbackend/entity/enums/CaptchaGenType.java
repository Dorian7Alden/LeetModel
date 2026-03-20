package com.senior.leetmodelbackend.entity.enums;

import lombok.Getter;

@Getter
public enum CaptchaGenType {
    SIX_DIGIT("sixDigitCode");

    private final String code;

    CaptchaGenType(String code) {
        this.code = code;
    }
}