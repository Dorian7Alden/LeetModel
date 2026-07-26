package com.senior.leetmodelbackend.common.validator;

import com.senior.leetmodelbackend.common.exception.BusinessException;
import com.senior.leetmodelbackend.common.exception.ResponseCode;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 链式参数校验器
 */
public class ParameterValidator {

    private final List<String> errors = new ArrayList<>();

    private ParameterValidator() {
    }

    public static ParameterValidator init() {
        return new ParameterValidator();
    }

    public ParameterValidator notNull(Object value, String msg) {
        if (value == null) {
            errors.add(msg);
        }
        return this;
    }

    public ParameterValidator hasLength(String str, String msg) {
        if (!StringUtils.hasLength(str) || str.trim().isEmpty()) {
            errors.add(msg);
        }
        return this;
    }

    public ParameterValidator notEmpty(Collection<?> collection, String msg) {
        if (ObjectUtils.isEmpty(collection)) {
            errors.add(msg);
        }
        return this;
    }

    public ParameterValidator isTrue(boolean condition, String msg) {
        if (!condition) {
            errors.add(msg);
        }
        return this;
    }

    public ParameterValidator maxLength(String str, int max, String msg) {
        if (str != null && str.length() > max) {
            errors.add(msg);
        }
        return this;
    }

    public ParameterValidator minLength(String str, int min, String msg) {
        if (str != null && str.length() < min) {
            errors.add(msg);
        }
        return this;
    }

    public ParameterValidator range(Number num, double min, double max, String msg) {
        if (num != null && (num.doubleValue() < min || num.doubleValue() > max)) {
            errors.add(msg);
        }
        return this;
    }

    public ParameterValidator sizeRange(Collection<?> col, int min, int max, String msg) {
        if (col != null && (col.size() < min || col.size() > max)) {
            errors.add(msg);
        }
        return this;
    }

    public void validateAndThrow() {
        if (!errors.isEmpty()) {
            throw new BusinessException(ResponseCode.PARAM_VALIDATION_ERROR, String.join("; ", errors));
        }
    }
}