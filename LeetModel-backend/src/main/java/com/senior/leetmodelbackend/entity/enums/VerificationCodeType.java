package com.senior.leetmodelbackend.entity.enums;

import lombok.Getter;

@Getter
public enum VerificationCodeType {
    EMAIL("email");

    private final String code;

    VerificationCodeType(String code) {
        this.code = code;
    }
}