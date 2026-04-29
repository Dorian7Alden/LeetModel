package leet.model.leetmodelbackend.common.validator;

/**
 * 前端入参校验器接口，每个 Controller 接口方法对应一个实现类。
 * 与 {@link ParameterValidator} 配合使用：实现类内部使用 ParameterValidator 链式完成校验。
 *
 * @param <T> 请求 DTO 类型
 */
@FunctionalInterface
public interface ParamValidator<T> {

    /** 执行参数校验，校验不通过时抛出 {@link leet.model.leetmodelbackend.common.error.BusinessException} */
    void validate(T request);
}
