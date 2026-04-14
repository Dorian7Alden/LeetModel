package com.senior.leetmodelbackend.pojo.enums.error;

/**
 * 基础错误码接口，所有业务错误码枚举都需要实现此接口
 * 方便在异常类和返回结果中进行统一类型接收
 */
public interface BaseErrorCode {
    /**
     * 获取状态码
     * @return 5 位数业务状态码
     */
    int getCode();
    String getMsg();
}
