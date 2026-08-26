package com.leetmodel.common.core.handler;

import com.leetmodel.common.core.result.Result;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Positive;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void convertsMethodParameterViolationToReadableClientError() throws Exception {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        Boundary boundary = new Boundary();
        Method method = Boundary.class.getDeclaredMethod("load", Long.class);
        Set<ConstraintViolation<Boundary>> violations = validator.forExecutables()
                .validateParameters(boundary, method, new Object[]{0L});

        Result<?> result = handler.handleConstraintViolation(
                new ConstraintViolationException(violations));

        assertThat(result.getCode()).isEqualTo(40001);
        assertThat(result.getMessage()).isEqualTo("题目标识必须为正整数");
    }

    private static class Boundary {
        void load(@Positive(message = "题目标识必须为正整数") Long problemId) {
        }
    }
}
