package com.leetmodel.common.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 规划算子全局执行产物。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskPlanResultDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Integer totalQuestions;
    private List<SubTaskPlanDTO> tasks;
    private String plannerModelVersion;
}
