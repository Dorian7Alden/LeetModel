package com.leetmodel.admin.service;

import com.leetmodel.admin.enums.AdminErrorCode;
import com.leetmodel.common.core.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** 将 Feign 传输失败转换为稳定且不泄露内部信息的管理端响应。 */
@Slf4j
@Component
public class AdminFeignExecutor {

    public <T> Result<T> forward(String serviceName, RemoteCall<T> call) {
        try {
            Result<T> result = call.get();
            if (result != null) return result;
        } catch (RuntimeException exception) {
            log.warn("管理端下游调用失败 service={}, type={}", serviceName,
                    exception.getClass().getSimpleName());
        }
        return Result.fail(AdminErrorCode.SERVICE_UNAVAILABLE.getCode(), serviceName + "暂不可用");
    }

    @FunctionalInterface
    public interface RemoteCall<T> {
        Result<T> get();
    }
}
