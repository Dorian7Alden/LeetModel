package com.leetmodel.aigateway.provider;

import com.leetmodel.aigateway.enums.AiGatewayErrorCode;
import com.leetmodel.common.core.exception.BusinessException;

import java.time.Duration;

/** 携带 new-api Retry-After 的稳定限流异常，不包含上游响应正文。 */
public class AiUpstreamRateLimitException extends BusinessException {
    private final Duration retryAfter;

    public AiUpstreamRateLimitException(Duration retryAfter) {
        super(AiGatewayErrorCode.UPSTREAM_RATE_LIMITED);
        this.retryAfter = retryAfter;
    }

    public Duration getRetryAfter() {
        return retryAfter;
    }
}
