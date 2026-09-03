package com.leetmodel.common.api.dto;

import java.util.List;

/** 有界审计查询页；hasMore 只表示存在下一页，不暴露总量扫描。 */
public record OperationAuditPageDTO(
        List<OperationAuditEventDTO> events,
        boolean hasMore,
        int returned
) { }
