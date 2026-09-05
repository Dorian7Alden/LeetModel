package com.leetmodel.common.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 阶段二动态任务规划输出契约。
 * 承载了单个评审子任务的全部元数据，作为步骤 2.2 任务初始化的唯一指导依据。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubTaskPlanDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String taskId;
    private String taskType;
    private String taskName;
    private Integer targetQuestionNo;
    private SubProblemCategoryDTO subProblemCategory;
    private List<SectionAnchorDTO> suggestedSectionAnchors;
    private List<String> evaluationObjectives;
    private String suggestedKnowledgeQuery;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SectionAnchorDTO implements Serializable {
        private static final long serialVersionUID = 1L;

        private String sectionId;
        private String title;
        private String startBlockId;
        private String endBlockId;
        private Integer physicalPage;
        private Double matchConfidence;
    }
}
