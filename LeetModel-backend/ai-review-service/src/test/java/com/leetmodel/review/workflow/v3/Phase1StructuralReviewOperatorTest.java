package com.leetmodel.review.workflow.v3;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leetmodel.common.ai.model.AiProvider;
import com.leetmodel.common.ai.client.AiClient;
import com.leetmodel.common.ai.model.AiChatResponse;
import com.leetmodel.common.api.dto.Phase1StructuralReviewResultDTO;
import com.leetmodel.review.entity.ReviewTask;
import com.leetmodel.review.parse.v2.PaperDocumentV2;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class Phase1StructuralReviewOperatorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldExecutePhase1ReviewSuccessfully() {
        AiClient aiClient = mock(AiClient.class);
        String jsonResponse = """
                {
                  "score": 23.0,
                  "aspects": [
                    {"aspectCode": "ABSTRACT_STRUCTURE", "score": 9.0},
                    {"aspectCode": "PROBLEM_ANALYSIS_STRUCTURE", "score": 4.5},
                    {"aspectCode": "ASSUMPTION_NOMENCLATURE", "score": 4.5},
                    {"aspectCode": "CODE_LAYOUT_AESTHETICS", "score": 5.0}
                  ],
                  "findings": [
                    {"findingId": "F_P1_001", "type": "STRENGTH", "statement": "摘要四要素完整，给出了具体数值。"}
                  ]
                }
                """;
        when(aiClient.chat(any())).thenReturn(new AiChatResponse(
                "call-1", AiProvider.NEW_API, "deepseek-chat", "resp-1", jsonResponse, null, "stop", null));

        Phase1SliceExtractor extractor = new Phase1SliceExtractor();
        Phase1StructuralReviewOperator operator = new Phase1StructuralReviewOperator(aiClient, objectMapper, extractor);

        ReviewTask task = new ReviewTask();
        task.setId(1001L);
        task.setAttemptNo(1);

        PaperDocumentV2 doc = new PaperDocumentV2(
                PaperDocumentV2.SCHEMA_VERSION, 1001L, "sha256",
                new PaperDocumentV2.DocumentMetadata(10, 8000, "测试", "ZH", "PAPER_PARSE_V2", "2026-09-05T10:00:00Z"),
                new PaperDocumentV2.LayoutAesthetics(90.0, "HIGH", "EXCELLENT", "良好"),
                List.of(new PaperDocumentV2.ContentBlockV2("B1", PaperDocumentV2.BlockType.PARAGRAPH, 1, "摘要内容，得出数值32.4", null, null, null, null, null, List.of())),
                List.of(new PaperDocumentV2.SectionIndex("S1", "摘要", 1, "B1", 1)),
                new PaperDocumentV2.DocumentQualityV2("SUCCESS", 10, 0, 2, 1, 1, 90.0, List.of())
        );

        Phase1StructuralReviewResultDTO result = operator.execute(task, doc);

        assertThat(result).isNotNull();
        assertThat(result.getScore()).isEqualTo(BigDecimal.valueOf(23.0).setScale(1));
        assertThat(result.getAspects()).hasSize(4);
        assertThat(result.getFindings()).hasSize(1);
    }

    @Test
    void shouldFallbackGracefullyWhenAiReturnsEmpty() {
        AiClient aiClient = mock(AiClient.class);
        when(aiClient.chat(any())).thenReturn(new AiChatResponse(
                "call-err", AiProvider.NEW_API, "model", "resp-2", "", null, "stop", null));

        Phase1SliceExtractor extractor = new Phase1SliceExtractor();
        Phase1StructuralReviewOperator operator = new Phase1StructuralReviewOperator(aiClient, objectMapper, extractor);

        ReviewTask task = new ReviewTask();
        task.setId(1002L);

        PaperDocumentV2 doc = new PaperDocumentV2(
                PaperDocumentV2.SCHEMA_VERSION, 1002L, "sha256",
                null, null, List.of(), List.of(), null
        );

        try {
            operator.execute(task, doc);
        } catch (Exception e) {
            assertThat(e).isInstanceOf(IllegalStateException.class);
        }
    }
}
