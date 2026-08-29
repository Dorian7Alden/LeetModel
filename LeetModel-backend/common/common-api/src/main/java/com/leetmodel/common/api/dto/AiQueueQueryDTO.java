package com.leetmodel.common.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

/** AI 队列元数据查询；不接受正文、结果或任意 SQL 条件。 */
@Data
@NoArgsConstructor
public class AiQueueQueryDTO {
    @Pattern(regexp = "QUEUED|LEASED|RUNNING|SUCCEEDED|FAILED|CANCELLED|EXPIRED")
    private String state;
    @Pattern(regexp = "P[0-4]")
    private String priority;
    @Size(max = 64)
    private String callerService;
    @Size(max = 128)
    private String evaluationTaskId;
    @Min(0)
    private Long minWaitMs;
    @Min(1)
    @Max(100)
    private Integer limit = 50;
}
