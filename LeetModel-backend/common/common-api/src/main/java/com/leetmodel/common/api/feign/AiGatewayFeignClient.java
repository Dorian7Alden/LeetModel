package com.leetmodel.common.api.feign;

import com.leetmodel.common.api.dto.AiCallLogDTO;
import com.leetmodel.common.api.dto.AiCallQueryDTO;
import com.leetmodel.common.api.dto.AiCallStatsDTO;
import com.leetmodel.common.api.dto.AiQueueQueryDTO;
import com.leetmodel.common.api.dto.AiQueueTaskDTO;
import com.leetmodel.common.api.dto.AiEvaluationCallAggregateDTO;
import com.leetmodel.common.api.dto.ModelExecutionConfigAvailabilityDTO;
import com.leetmodel.common.api.dto.AiModelCallStatsDTO;
import com.leetmodel.common.api.dto.AiCallFilterOptionsDTO;
import com.leetmodel.common.api.dto.AiProviderModelDTO;
import com.leetmodel.common.core.result.Result;
import com.leetmodel.common.core.result.PageResult;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.cloud.openfeign.SpringQueryMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/** AI 网关调用审计内部查询契约。 */
@FeignClient(name = "ai-gateway-service")
public interface AiGatewayFeignClient {

    @GetMapping("/internal/ai/calls")
    Result<List<AiCallLogDTO>> listCalls(@SpringQueryMap AiCallQueryDTO query);

    @GetMapping("/internal/ai/calls/page")
    Result<PageResult<AiCallLogDTO>> pageCalls(@SpringQueryMap AiCallQueryDTO query);

    @GetMapping("/internal/ai/calls/stats")
    Result<AiCallStatsDTO> getCallStats(@SpringQueryMap AiCallQueryDTO query);

    @GetMapping("/internal/ai/calls/model-stats")
    Result<List<AiModelCallStatsDTO>> getModelCallStats(@SpringQueryMap AiCallQueryDTO query);

    @GetMapping("/internal/ai/calls/filter-options")
    Result<AiCallFilterOptionsDTO> getCallFilterOptions();

    @GetMapping("/internal/ai/models/{provider}")
    Result<List<AiProviderModelDTO>> listProviderModels(@PathVariable("provider") String provider);

    @GetMapping("/internal/ai/tasks")
    Result<List<AiQueueTaskDTO>> listQueueTasks(@SpringQueryMap AiQueueQueryDTO query);

    @PostMapping("/internal/ai/tasks/{taskId}/cancel")
    Result<AiQueueTaskDTO> cancelQueueTask(@PathVariable("taskId") String taskId);

    @GetMapping("/internal/ai/evaluations/{evaluationTaskId}/aggregate")
    Result<AiEvaluationCallAggregateDTO> aggregateEvaluationCalls(
            @PathVariable("evaluationTaskId") String evaluationTaskId);

    @GetMapping("/internal/ai/model-execution-configs/{version}/availability")
    Result<ModelExecutionConfigAvailabilityDTO> getModelExecutionConfigAvailability(
            @PathVariable("version") String version,
            @RequestParam("callType") String callType,
            @RequestParam("workflowVersion") String workflowVersion,
            @RequestParam("promptVersion") String promptVersion);
}
