package leet.model.leetmodelbackend.common.validator;

import leet.model.leetmodelbackend.common.error.BusinessException;
import leet.model.leetmodelbackend.common.error.ResponseCode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 链式参数校验器，统一在 Controller 顶部完成请求参数校验。
 */
public class ParameterValidator {

    private final List<String> errors = new ArrayList<>();

    private ParameterValidator() {
    }

    public static ParameterValidator init() {
        return new ParameterValidator();
    }

    public ParameterValidator notNull(Object value, String message) {
        if (value == null) {
            errors.add(message);
        }
        return this;
    }

    public ParameterValidator hasLength(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            errors.add(message);
        }
        return this;
    }

    public ParameterValidator notEmpty(Collection<?> collection, String message) {
        if (collection == null || collection.isEmpty()) {
            errors.add(message);
        }
        return this;
    }

    public ParameterValidator isTrue(boolean expression, String message) {
        if (!expression) {
            errors.add(message);
        }
        return this;
    }

    public void validateAndThrow() {
        if (!errors.isEmpty()) {
            throw new BusinessException(ResponseCode.GLOBAL_PARAM_VALIDATION_ERROR, String.join("; ", errors));
        }
    }
}