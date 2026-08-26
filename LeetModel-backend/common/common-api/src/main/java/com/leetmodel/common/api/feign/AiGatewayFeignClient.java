package com.leetmodel.common.api.feign;

import com.leetmodel.common.api.dto.AiCallLogDTO;
import com.leetmodel.common.api.dto.AiCallQueryDTO;
import com.leetmodel.common.api.dto.AiCallStatsDTO;
import com.leetmodel.common.core.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.cloud.openfeign.SpringQueryMap;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

/** AI 网关调用审计内部查询契约。 */
@FeignClient(name = "ai-gateway-service")
public interface AiGatewayFeignClient {

    @GetMapping("/internal/ai/calls")
    Result<List<AiCallLogDTO>> listCalls(@SpringQueryMap AiCallQueryDTO query);

    @GetMapping("/internal/ai/calls/stats")
    Result<AiCallStatsDTO> getCallStats();
}
