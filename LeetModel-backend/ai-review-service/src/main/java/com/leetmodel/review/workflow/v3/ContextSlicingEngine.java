package com.leetmodel.review.workflow.v3;

import com.leetmodel.common.api.dto.KnowledgeCitationDTO;
import com.leetmodel.common.api.dto.KnowledgeRetrievalRequestDTO;
import com.leetmodel.common.api.dto.KnowledgeRetrievalResultDTO;
import com.leetmodel.common.api.dto.ProblemContextDTO;
import com.leetmodel.common.api.dto.SubTaskPlanDTO;
import com.leetmodel.common.api.dto.TaskAssembledContextDTO;
import com.leetmodel.common.api.feign.KnowledgeRetrievalFeignClient;
import com.leetmodel.common.core.result.Result;
import com.leetmodel.review.parse.v2.PaperDocumentV2;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * 阶段二上下文切片提取与双轨装配引擎。
 * 核心机制：
 * 1. 强制前置吸附公共底座（全局模型假设 + 符号说明表 HTML table）；
 * 2. 依据子任务规划锚点精准切片正文公式、表格与代码；
 * 3. 联动知识检索服务召回权威题型规则；
 * 4. 组装输出自洽、Token 受控在 25k~35k 内的 TaskAssembledContextDTO。
 */
@Slf4j
@Component
public class ContextSlicingEngine {

    private final KnowledgeRetrievalFeignClient knowledgeFeignClient;

    public ContextSlicingEngine(@Autowired(required = false) KnowledgeRetrievalFeignClient knowledgeFeignClient) {
        this.knowledgeFeignClient = knowledgeFeignClient;
    }

    /**
     * 为指定的子任务规划装配完整的上下文。
     */
    public TaskAssembledContextDTO assembleContext(
            PaperDocumentV2 document,
            SubTaskPlanDTO taskPlan,
            ProblemContextDTO problem) {

        // 1. 强制前置吸附公共公理底座：模型假设与符号说明表
        List<TaskAssembledContextDTO.PaperSliceBlockDTO> assumptions = extractAssumptions(document);
        List<TaskAssembledContextDTO.PaperSliceBlockDTO> nomenclature = extractNomenclature(document);

        // 2. 目标小题正文 Blocks 精准提取
        List<TaskAssembledContextDTO.PaperSliceBlockDTO> targetBlocks = extractTargetBlocks(document, taskPlan);

        // 3. 外部权威标准检索轨
        List<KnowledgeCitationDTO> citations = retrieveKnowledge(taskPlan);

        // 4. 提取该小题专属题面要求
        String questionMarkdown = extractQuestionMarkdown(problem, taskPlan.getTargetQuestionNo());

        // 5. 估算总体 Token
        int estimatedTokens = estimateTokens(assumptions, nomenclature, targetBlocks, citations) + (questionMarkdown != null ? questionMarkdown.length() / 2 : 0);

        return TaskAssembledContextDTO.builder()
                .taskPlan(taskPlan)
                .problemQuestionMarkdown(questionMarkdown)
                .attachedAssumptions(assumptions)
                .attachedNomenclature(nomenclature)
                .targetSectionBlocks(targetBlocks)
                .knowledgeCitations(citations)
                .estimatedTotalTokens(estimatedTokens)
                .build();
    }

    private List<TaskAssembledContextDTO.PaperSliceBlockDTO> extractAssumptions(PaperDocumentV2 document) {
        List<TaskAssembledContextDTO.PaperSliceBlockDTO> list = new ArrayList<>();
        List<PaperDocumentV2.SectionIndex> sections = document.sections();
        if (sections == null) return list;
        for (var sec : sections) {
            if (sec.title().contains("假设") || sec.title().contains("前提")) {
                List<PaperDocumentV2.ContentBlockV2> blocks = getSectionBlocks(document, sec.headingBlockId());
                for (var b : blocks) {
                    list.add(toSliceBlock(b));
                }
                break;
            }
        }
        return list;
    }

    private List<TaskAssembledContextDTO.PaperSliceBlockDTO> extractNomenclature(PaperDocumentV2 document) {
        List<TaskAssembledContextDTO.PaperSliceBlockDTO> list = new ArrayList<>();
        List<PaperDocumentV2.SectionIndex> sections = document.sections();
        if (sections == null) return list;
        for (var sec : sections) {
            if (sec.title().contains("符号") || sec.title().contains("说明") || sec.title().contains("名词")) {
                List<PaperDocumentV2.ContentBlockV2> blocks = getSectionBlocks(document, sec.headingBlockId());
                for (var b : blocks) {
                    list.add(toSliceBlock(b));
                }
                break;
            }
        }
        return list;
    }

    private List<TaskAssembledContextDTO.PaperSliceBlockDTO> extractTargetBlocks(
            PaperDocumentV2 document, SubTaskPlanDTO taskPlan) {
        List<TaskAssembledContextDTO.PaperSliceBlockDTO> result = new ArrayList<>();
        List<PaperDocumentV2.ContentBlockV2> blocks = document.blocks();
        if (blocks == null || blocks.isEmpty()) return result;

        // 如果是摘要核验任务，优先提取摘要章节与结果章节
        if ("ABSTRACT_VERIFICATION".equals(taskPlan.getTaskType())) {
            return extractAbstractAndResults(document);
        }

        // 如果规划携带了锚点
        if (taskPlan.getSuggestedSectionAnchors() != null && !taskPlan.getSuggestedSectionAnchors().isEmpty()) {
            for (var anchor : taskPlan.getSuggestedSectionAnchors()) {
                String startId = anchor.getStartBlockId();
                String endId = anchor.getEndBlockId();
                boolean inRange = false;
                for (var b : blocks) {
                    if (b.blockId().equals(startId)) {
                        inRange = true;
                    }
                    if (inRange) {
                        result.add(toSliceBlock(b));
                        if (endId != null && b.blockId().equals(endId)) {
                            break;
                        }
                    }
                }
            }
        }

        // 若锚点未匹配到任何块，使用基于标题和内容的模糊搜索
        if (result.isEmpty()) {
            result = fallbackSearchBlocks(document, taskPlan);
        }
        return result;
    }

    private List<TaskAssembledContextDTO.PaperSliceBlockDTO> extractAbstractAndResults(PaperDocumentV2 document) {
        List<TaskAssembledContextDTO.PaperSliceBlockDTO> result = new ArrayList<>();
        if (document.blocks() == null) return result;
        for (var b : document.blocks()) {
            // 提取第 1 页摘要
            if (b.physicalPage() <= 2 && b.type() == PaperDocumentV2.BlockType.PARAGRAPH) {
                result.add(toSliceBlock(b));
            }
            // 提取所有表格 (核对数据一致性)
            if (b.type() == PaperDocumentV2.BlockType.TABLE) {
                result.add(toSliceBlock(b));
            }
        }
        return result;
    }

    private List<TaskAssembledContextDTO.PaperSliceBlockDTO> fallbackSearchBlocks(
            PaperDocumentV2 document, SubTaskPlanDTO taskPlan) {
        List<TaskAssembledContextDTO.PaperSliceBlockDTO> result = new ArrayList<>();
        int targetQ = taskPlan.getTargetQuestionNo() == null ? 1 : taskPlan.getTargetQuestionNo();
        String keyword = switch (targetQ) {
            case 1 -> "问题一";
            case 2 -> "问题二";
            case 3 -> "问题三";
            case 4 -> "问题四";
            case 5 -> "问题五";
            default -> "问题" + targetQ;
        };
        List<PaperDocumentV2.SectionIndex> sections = document.sections();
        if (sections != null) {
            for (var sec : sections) {
                if (sec.title().contains(keyword)) {
                    for (var b : getSectionBlocks(document, sec.headingBlockId())) {
                        result.add(toSliceBlock(b));
                    }
                    break;
                }
            }
        }
        // 极端兜底：如果依然为空，提取中间页码对应段落
        if (result.isEmpty() && document.blocks() != null) {
            int totalPages = document.metadata() != null ? document.metadata().totalPages() : 10;
            int startPage = Math.max(1, (targetQ - 1) * (totalPages / 4) + 1);
            int endPage = Math.min(totalPages, startPage + 3);
            for (var b : document.blocks()) {
                if (b.physicalPage() >= startPage && b.physicalPage() <= endPage) {
                    result.add(toSliceBlock(b));
                }
            }
        }
        return result;
    }

    private List<KnowledgeCitationDTO> retrieveKnowledge(SubTaskPlanDTO taskPlan) {
        if (knowledgeFeignClient == null || taskPlan.getSubProblemCategory() == null) {
            return Collections.emptyList();
        }
        try {
            var category = taskPlan.getSubProblemCategory();
            String query = taskPlan.getSuggestedKnowledgeQuery();
            if (query == null || query.isBlank()) {
                query = String.format("数模评审 %s %s 评阅标准 踩坑指南",
                        category.getCategoryName(),
                        category.getTypicalMethods() != null ? String.join(" ", category.getTypicalMethods()) : "");
            }
            KnowledgeRetrievalRequestDTO request = new KnowledgeRetrievalRequestDTO();
            request.setWorkflowVersion("AI_DIRECTORY_V1");
            request.setScene(category.getRetrievalScene());
            request.setQuery(query);
            request.setTopK(4);
            request.setTokenBudget(2500);

            Result<KnowledgeRetrievalResultDTO> result = knowledgeFeignClient.retrieve(request);
            if (result != null && result.isSuccess() && result.getData() != null) {
                return result.getData().getCitations() != null ? result.getData().getCitations() : Collections.emptyList();
            }
        } catch (Exception exception) {
            log.warn("调用知识检索服务失败，降级为空知识列表: taskId={}, error={}",
                    taskPlan.getTaskId(), exception.getMessage());
        }
        return Collections.emptyList();
    }

    private String extractQuestionMarkdown(ProblemContextDTO problem, Integer questionNo) {
        if (problem == null || problem.getContentMarkdown() == null) {
            return "（无特定题面要求）";
        }
        if (questionNo == null || questionNo <= 0) {
            return problem.getContentMarkdown().length() <= 3000
                    ? problem.getContentMarkdown()
                    : problem.getContentMarkdown().substring(0, 3000) + "\n...（节选）";
        }
        SubProblemClassifier classifier = new SubProblemClassifier();
        var map = classifier.splitQuestions(problem.getContentMarkdown());
        String text = map.get(questionNo);
        if (text != null && !text.isBlank()) {
            return text;
        }
        return problem.getContentMarkdown();
    }

    private TaskAssembledContextDTO.PaperSliceBlockDTO toSliceBlock(PaperDocumentV2.ContentBlockV2 b) {
        return TaskAssembledContextDTO.PaperSliceBlockDTO.builder()
                .blockId(b.blockId())
                .blockType(b.type() != null ? b.type().name() : "PARAGRAPH")
                .physicalPage(b.physicalPage())
                .text(b.text())
                .latex(b.formula() != null ? b.formula().latex() : null)
                .formulaNo(b.formula() != null ? b.formula().formulaNo() : null)
                .htmlTable(b.table() != null ? b.table().html() : null)
                .codeContent(b.code() != null ? b.code().codeContent() : null)
                .codeLanguage(b.code() != null ? b.code().language() : null)
                .figureDescription(b.figure() != null ? b.figure().description() : null)
                .build();
    }

    private List<PaperDocumentV2.ContentBlockV2> getSectionBlocks(PaperDocumentV2 document, String headingBlockId) {
        List<PaperDocumentV2.ContentBlockV2> result = new ArrayList<>();
        List<PaperDocumentV2.ContentBlockV2> blocks = document.blocks();
        if (blocks == null) return result;
        boolean collecting = false;
        for (var b : blocks) {
            if (b.blockId().equals(headingBlockId)) {
                collecting = true;
                continue;
            }
            if (collecting) {
                if (b.type() == PaperDocumentV2.BlockType.HEADING) {
                    break;
                }
                result.add(b);
            }
        }
        return result;
    }

    private int estimateTokens(List<?>... lists) {
        int totalChars = 0;
        for (var l : lists) {
            if (l != null) totalChars += l.size() * 150;
        }
        return Math.max(1000, totalChars / 2);
    }
}
