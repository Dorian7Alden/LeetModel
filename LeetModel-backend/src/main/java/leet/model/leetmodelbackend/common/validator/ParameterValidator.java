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

    /** 创建新的校验链 */
    public static ParameterValidator init() {
        return new ParameterValidator();
    }

    /** 校验非空，拦截 null */
    public ParameterValidator notNull(Object value, String message) {
        if (value == null) {
            errors.add(message);
        }
        return this;
    }

    /** 校验字符串不为空且不全是空白字符 */
    public ParameterValidator hasLength(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            errors.add(message);
        }
        return this;
    }

    /** 校验集合不为空且至少包含一个元素 */
    public ParameterValidator notEmpty(Collection<?> collection, String message) {
        if (collection == null || collection.isEmpty()) {
            errors.add(message);
        }
        return this;
    }

    /** 校验布尔条件为 true */
    public ParameterValidator isTrue(boolean expression, String message) {
        if (!expression) {
            errors.add(message);
        }
        return this;
    }

    /** 收集到的全部错误用分号拼接后抛出 BusinessException */
    public void validateAndThrow() {
        if (!errors.isEmpty()) {
            throw new BusinessException(ResponseCode.GLOBAL_PARAM_VALIDATION_ERROR, String.join("; ", errors));
        }
    }
}