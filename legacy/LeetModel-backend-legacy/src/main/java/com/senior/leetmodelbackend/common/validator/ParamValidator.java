package com.senior.leetmodelbackend.common.validator;

@FunctionalInterface
public interface ParamValidator<T> {
    void validate(T request);
}
