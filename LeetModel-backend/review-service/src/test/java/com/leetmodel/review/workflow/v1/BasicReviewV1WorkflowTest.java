package com.leetmodel.review.workflow.v1;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leetmodel.common.ai.client.AiClient;
import com.leetmodel.common.ai.model.AiChatResponse;
import com.leetmodel.common.ai.model.AiProvider;
import com.leetmodel.common.api.dto.SubmissionReviewDTO;
import com.leetmodel.common.core.storage.StorageService;
import com.leetmodel.review.entity.ReviewTask;
import com.leetmodel.review.service.ReviewTaskLogService;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BasicReviewV1WorkflowTest {
    private StorageService storageService;
    private AiClient aiClient;
    private BasicReviewV1Workflow workflow;
    private byte[] pdf;

    @BeforeEach
    void setUp() throws Exception {
        storageService = mock(StorageService.class);
        aiClient = mock(AiClient.class);
        ReviewTaskLogService logService = mock(ReviewTaskLogService.class);
        BasicReviewV1Properties properties = new BasicReviewV1Properties();
        properties.setRenderDpi(72);
        workflow = new BasicReviewV1Workflow(storageService, new PdfPageRenderer(properties), aiClient,
                new ObjectMapper(), logService);
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.addPage(new PDPage()); document.save(output); pdf = output.toByteArray();
        }
        when(storageService.download("paper.pdf")).thenAnswer(ignored -> new ByteArrayInputStream(pdf));
    }

    @Test
    void renderPdfAndAcceptValidTypedJson() throws Exception {
        when(aiClient.chat(any())).thenReturn(response("""
                {"score":82.5,"summary":"完整且自洽","dimensions":{
                "assumptionRationality":{"score":80,"comment":"假设清楚"},
                "modelCreativity":{"score":83,"comment":"方法合适"},
                "resultCorrectness":{"score":82,"comment":"结果有支撑"},
                "expressionClarity":{"score":85,"comment":"表达清晰"}},
                "strengths":["结构完整"],"weaknesses":["验证较少"],"suggestions":["补充敏感性分析"]}
                """));
        assertEquals("82.5", workflow.execute(task(), submission()).score().toPlainString());
    }

    @Test
    void rejectJsonWithoutRequiredDimensions() {
        when(aiClient.chat(any())).thenReturn(response("{\"score\":82.5,\"summary\":\"ok\"}"));
        assertThrows(IllegalArgumentException.class, () -> workflow.execute(task(), submission()));
    }

    private ReviewTask task() {
        ReviewTask task = new ReviewTask(); task.setId(1L); task.setAttemptNo(1);
        task.setWorkflowVersion(BasicReviewV1Workflow.VERSION_CODE); task.setPromptSnapshot(workflow.currentPrompt());
        return task;
    }
    private SubmissionReviewDTO submission() {
        return new SubmissionReviewDTO(2L, 3L, 4L, 1, "paper.pdf");
    }
    private AiChatResponse response(String content) {
        return new AiChatResponse("call-1", AiProvider.DEEPSEEK, "deepseek-v4-flash-vision-exp",
                "provider-1", content, null, "stop", null);
    }
}
