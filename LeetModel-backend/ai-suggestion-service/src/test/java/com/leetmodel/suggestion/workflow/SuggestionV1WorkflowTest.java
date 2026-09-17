package com.leetmodel.suggestion.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leetmodel.common.ai.client.AiClient;
import com.leetmodel.common.ai.model.AiChatResponse;
import com.leetmodel.common.ai.model.AiModality;
import com.leetmodel.common.ai.model.AiOperationCode;
import com.leetmodel.common.ai.model.AiProvider;
import com.leetmodel.common.api.dto.ProblemContextDTO;
import com.leetmodel.common.api.dto.ReviewSummaryDTO;
import com.leetmodel.common.api.dto.SubmissionReviewDTO;
import com.leetmodel.common.core.storage.StorageService;
import com.leetmodel.suggestion.entity.SuggestionTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SuggestionV1WorkflowTest {

    @Mock
    private StorageService storageService;
    @Mock
    private PdfTextExtractor textExtractor;
    @Mock
    private AiClient aiClient;

    private SuggestionV1Workflow workflow;

    @BeforeEach
    void setUp() throws Exception {
        workflow = new SuggestionV1Workflow(
                storageService, textExtractor, aiClient, new ObjectMapper());
    }

    @Test
    void sendsGroundedContextAndAcceptsValidStructuredOutput() throws Exception {
        preparePaper();
        String json = "{\"summary\":\"先补充敏感性分析\",\"items\":[{"
                + "\"priority\":\"HIGH\",\"category\":\"VALIDATION\","
                + "\"title\":\"补参数检验\",\"action\":\"改变参数并重算\","
                + "\"evidence\":\"第2页只给出单组结果\",\"page\":2}]}";
        when(aiClient.chat(any())).thenReturn(new AiChatResponse(
                "call-s", AiProvider.NEW_API, "model-s", "provider-1", json,
                null, "stop", null));

        SuggestionWorkflowResult result = workflow.execute(
                task(), submission(), problem(), review());

        assertThat(result.aiCallId()).isEqualTo("call-s");
        assertThat(result.resultJson()).contains("先补充敏感性分析");
        ArgumentCaptor<com.leetmodel.common.ai.model.AiChatRequest> captor =
                ArgumentCaptor.forClass(com.leetmodel.common.ai.model.AiChatRequest.class);
        verify(aiClient).chat(captor.capture());
        assertThat(captor.getValue().modality()).isEqualTo(AiModality.TEXT);
        assertThat(captor.getValue().context().operationCode())
                .isEqualTo(AiOperationCode.GENERATE_SUGGESTION);
        String userText = captor.getValue().messages().get(1).content().get(0).text();
        assertThat(userText).contains("调度题", "已有评审", "第 2 页", "论文原文");
    }

    @Test
    void rejectsModelPageOutsidePaperRange() throws Exception {
        preparePaper();
        String json = "{\"summary\":\"建议\",\"items\":[{"
                + "\"priority\":\"HIGH\",\"category\":\"MODEL\","
                + "\"title\":\"标题\",\"action\":\"行动\","
                + "\"evidence\":\"依据\",\"page\":99}]}";
        when(aiClient.chat(any())).thenReturn(new AiChatResponse(
                "call-s", AiProvider.NEW_API, "model-s", null, json,
                null, "stop", null));

        assertThatThrownBy(() -> workflow.execute(task(), submission(), problem(), review()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("page");
    }

    @Test
    void rejectsItemsThatAreNotSortedByPriority() throws Exception {
        preparePaper();
        String json = "{\"summary\":\"建议\",\"items\":["
                + itemJson("LOW", 1) + "," + itemJson("HIGH", 2) + "]}";
        when(aiClient.chat(any())).thenReturn(new AiChatResponse(
                "call-s", AiProvider.NEW_API, "model-s", null, json,
                null, "stop", null));

        assertThatThrownBy(() -> workflow.execute(task(), submission(), problem(), review()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("优先级排序");
    }

    private String itemJson(String priority, int page) {
        return "{\"priority\":\"" + priority + "\",\"category\":\"MODEL\","
                + "\"title\":\"标题\",\"action\":\"行动\","
                + "\"evidence\":\"依据\",\"page\":" + page + "}";
    }

    private void preparePaper() throws Exception {
        when(storageService.download("object")).thenReturn(new ByteArrayInputStream(new byte[]{1, 2, 3}));
        when(textExtractor.extract(any())).thenReturn(
                new PdfTextExtractor.ExtractedPaper("[第 2 页]\n论文原文", 2, false));
    }

    private SuggestionTask task() {
        SuggestionTask task = new SuggestionTask();
        task.setPromptSnapshot(workflow.currentPrompt());
        return task;
    }

    private SubmissionReviewDTO submission() {
        return new SubmissionReviewDTO(101L, 11L, 51L, 2, "object");
    }

    private ProblemContextDTO problem() {
        return new ProblemContextDTO(51L, "调度题", "建立调度模型", 60, 1);
    }

    private ReviewSummaryDTO review() {
        return new ReviewSummaryDTO(
                5001L, 101L, 11L, 51L, "COMPLETED", "BASIC_REVIEW_V1",
                BigDecimal.valueOf(88), "{\"summary\":\"缺少敏感性分析\"}",
                "model-r", "call-r", null, LocalDateTime.now());
    }
}
