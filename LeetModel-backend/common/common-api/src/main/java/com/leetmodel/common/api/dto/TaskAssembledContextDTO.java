package com.leetmodel.common.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 阶段二任务就绪上下文装配契约。
 * 汇聚了双轨检索（外部权威 RAG + 内部切片与前置吸附）的全部事实资产。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskAssembledContextDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private SubTaskPlanDTO taskPlan;
    private String problemQuestionMarkdown;
    private List<PaperSliceBlockDTO> attachedAssumptions;
    private List<PaperSliceBlockDTO> attachedNomenclature;
    private List<PaperSliceBlockDTO> targetSectionBlocks;
    private List<KnowledgeCitationDTO> knowledgeCitations;
    private Integer estimatedTotalTokens;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaperSliceBlockDTO implements Serializable {
        private static final long serialVersionUID = 1L;

        private String blockId;
        private String blockType;
        private Integer physicalPage;
        private String text;
        private String latex;
        private String formulaNo;
        private String htmlTable;
        private String codeContent;
        private String codeLanguage;
        private String figureDescription;
    }
}
