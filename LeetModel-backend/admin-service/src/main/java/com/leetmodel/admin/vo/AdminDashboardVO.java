package com.leetmodel.admin.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/** 管理看板真实统计与各下游可用性。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminDashboardVO {
    private Map<String, AdminMetricVO> metrics = new LinkedHashMap<>();
    private LocalDateTime generatedAt;
    private boolean partialFailure;
}
