package com.leetmodel.suggestion.workflow.v2;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leetmodel.common.ai.client.AiClient;
import com.leetmodel.common.ai.model.AiChatResponse;
import com.leetmodel.common.ai.model.AiProvider;
import com.leetmodel.common.api.dto.KnowledgeCitationDTO;
import com.leetmodel.common.api.dto.KnowledgeRetrievalResultDTO;
import com.leetmodel.common.api.dto.PaperParseDTO;
import com.leetmodel.common.api.dto.ProblemContextDTO;
import com.leetmodel.suggestion.entity.SuggestionTask;
import com.leetmodel.suggestion.service.evidence.ReviewEvidenceSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GroundedSuggestionV2WorkflowTest {
    private AiClient aiClient;
    private GroundedSuggestionV2Workflow workflow;

    @BeforeEach
    void setUp() throws Exception {
        aiClient = mock(AiClient.class);
        workflow = new GroundedSuggestionV2Workflow(aiClient, new ObjectMapper());
    }

    @Test
    void acceptsOnlySuggestionWithPaperReviewAndKnowledgeChain() throws Exception {
        when(aiClient.chat(any())).thenReturn(response(validJson("KC-1")));

        var result = workflow.execute(task(), problem(), parse(), review(), knowledge());

        assertThat(result.resultJson()).contains("P2-B1", "F-1", "KC-1", "验收");
    }

    @Test
    void rejectsCitationNotReturnedByLockedRetrievalRun() {
        when(aiClient.chat(any())).thenReturn(response(validJson("KC-FORGED")));

        assertThatThrownBy(() -> workflow.execute(task(), problem(), parse(), review(), knowledge()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("knowledgeCitationIds");
    }

    private String validJson(String citationId) {
        return "{\"overallStrategy\":\"先补验证\",\"topPriorities\":[\"补充参数扰动\"],\"items\":[{"
                + "\"suggestionId\":\"S-1\",\"priority\":\"P1\",\"category\":\"VALIDATION\","
                + "\"problem\":\"当前只有单组结果\",\"impact\":\"无法判断稳健性\","
                + "\"target\":{\"physicalPages\":[2],\"section\":\"模型检验\"},"
                + "\"actions\":[\"对参数做正负10%扰动并重算\"],\"acceptanceCriteria\":[\"验收：报告排序变化和最大偏差\"],"
                + "\"paperEvidenceIds\":[\"P2-B1\"],\"reviewFindingIds\":[\"F-1\"],"
                + "\"knowledgeCitationIds\":[\"" + citationId + "\"]}]}";
    }

    private AiChatResponse response(String json) {
        return new AiChatResponse("call", AiProvider.NEW_API, "model", null, json,
                null, "stop", null);
    }

    private SuggestionTask task() {
        SuggestionTask task = new SuggestionTask();
        task.setId(88L); task.setPromptSnapshot(workflow.currentPrompt()); task.setAttemptNo(1);
        return task;
    }

    private ProblemContextDTO problem() {
        return new ProblemContextDTO(51L, "调度优化", "建立模型并验证", 180, 1);
    }

    private PaperParseDTO parse() {
        String json = "{\"schemaVersion\":\"PAPER_DOCUMENT_V1\",\"pageCount\":2,"
                + "\"pages\":[{\"physicalPage\":1,\"blockId\":\"P1-B1\"},{\"physicalPage\":2,\"blockId\":\"P2-B1\"}]}";
        return new PaperParseDTO(7L, 101L, "PAPER_PARSE_V1", "PAPER_DOCUMENT_V1",
                "hash", "SUCCESS", 2, false, "{}", json, null);
    }

    private ReviewEvidenceSnapshot review() {
        var finding = new ReviewEvidenceSnapshot.Finding("F-1", "ISSUE", "VALIDATION", "HIGH",
                "缺少稳健性检验", "结果可信度不足", "$.findings[0]", List.of("P2-B1"));
        return new ReviewEvidenceSnapshot(9L, 9L, "EVIDENCE_REVIEW_V2", null,
                List.of(finding), "{\"findings\":[{\"findingId\":\"F-1\"}]}");
    }

    private KnowledgeRetrievalResultDTO knowledge() {
        var citation = new KnowledgeCitationDTO("KC-1", "D1", "C1", "敏感性分析", "数学建模/论文评审/敏感性.md",
                null, "hash", "L3", "GENERAL_MODELING", 0.9, "参数扰动应报告范围和结果变化");
        return new KnowledgeRetrievalResultDTO("run-1", "AI_DIRECTORY_V1", "DIRECTORY",
                null, "manifest", "source", "COMPLETED", List.of(citation));
    }
}
