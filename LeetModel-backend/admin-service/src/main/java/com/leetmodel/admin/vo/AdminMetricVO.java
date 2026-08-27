package com.leetmodel.admin.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 单项看板指标；不可用时 value 保持 null，避免与真实零值混淆。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminMetricVO {
    private boolean available;
    private Long value;
    private String message;

    public static AdminMetricVO available(Long value) {
        return new AdminMetricVO(true, value, null);
    }

    public static AdminMetricVO unavailable(String message) {
        return new AdminMetricVO(false, null, message);
    }
}
