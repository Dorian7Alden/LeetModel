package com.leetmodel.common.ai.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * 不包含正文的单次 AI 业务调用上下文。
 *
 * <p>所有业务标识都按不透明字符串传递，AI 网关只用于关联、调度和查询，
 * 不解释它们在调用方数据库中的实体语义。</p>
 */
public record AiCallContext(
        @NotBlank @Size(max = 64) String callerService,
        @NotNull AiFeatureCode featureCode,
        @NotNull AiOperationCode operationCode,
        @Size(max = 128) String businessTaskId,
        @Size(max = 64) String workflowVersion,
        @Size(max = 100) String promptVersion,
        @Size(max = 100) String modelExecutionConfigVersion,
        @Size(max = 128) String evaluationTaskId,
        @NotNull AiCallPriority priority,
        @NotBlank @Size(max = 128) String idempotencyKey,
        @NotNull @Future Instant deadline
) {
    @JsonIgnore
    @AssertTrue(message = "operationCode 与 featureCode 不匹配")
    public boolean isOperationCompatible() {
        return operationCode == null || featureCode == null || operationCode.belongsTo(featureCode);
    }
}
