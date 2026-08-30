package com.leetmodel.common.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** AI 调用审计查询条件；所有字段只匹配结构化元数据。 */
@Data
@NoArgsConstructor
public class AiCallQueryDTO {
    @Deprecated @Size(max = 30) private String scene;
    @Size(max = 20) private String modality;
    @Size(max = 64) private String callerService;
    @Size(max = 64) private String featureCode;
    @Size(max = 64) private String operationCode;
    @Size(max = 64) private String callId;
    @Size(max = 128) private String businessTaskId;
    @Size(max = 128) private String evaluationTaskId;
    @Size(max = 128) private String ragIndexVersion;
    @Size(max = 64) private String workflowVersion;
    @Size(max = 100) private String promptVersion;
    @Size(max = 100) private String modelExecutionConfigVersion;
    @Size(max = 30) private String provider;
    @Size(max = 100) private String model;
    @Size(max = 20) private String status;
    @Size(max = 40) private String costSource;
    private LocalDateTime createdFrom;
    private LocalDateTime createdTo;
    @Min(1) @Max(100) private Integer limit = 20;
    @Min(1) private Integer page = 1;
    @Min(10) @Max(100) private Integer pageSize = 20;

    public AiCallQueryDTO(String scene, String provider, String model, String status, Integer limit) {
        this.scene = scene;
        this.provider = provider;
        this.model = model;
        this.status = status;
        this.limit = limit;
    }

    @JsonIgnore
    @java.beans.Transient
    @AssertTrue(message = "查询开始时间不能晚于结束时间")
    public boolean isTimeRangeValid() {
        return createdFrom == null || createdTo == null || !createdFrom.isAfter(createdTo);
    }
}
