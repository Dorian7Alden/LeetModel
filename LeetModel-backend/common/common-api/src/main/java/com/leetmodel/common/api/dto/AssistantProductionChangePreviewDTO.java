package com.leetmodel.common.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/** 服务端冻结的生产变更差异与第二次确认凭据。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AssistantProductionChangePreviewDTO {
    private String changeRequestId;
    private String action;
    private String status;
    private Long expectedRevision;
    private AssistantProductionConfigDTO current;
    private AssistantProductionConfigDTO target;
    private List<String> differences;
    private String impactScope;
    private String reason;
    private LocalDateTime expiresAt;
}
