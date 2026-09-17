package com.leetmodel.review.workflow.v1;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leetmodel.common.ai.client.HttpAiClient;
import com.leetmodel.common.ai.model.AiChatResponse;
import com.leetmodel.common.api.dto.SubmissionReviewDTO;
import com.leetmodel.common.core.storage.StorageService;
import com.leetmodel.review.entity.ReviewTask;
import com.leetmodel.review.service.ReviewTaskLogService;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.client.RestClient;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 使用合成单页 PDF 验证 review → common-ai → AI 网关 → new-api 多模态链路。 */
@EnabledIfEnvironmentVariable(named = "RUN_NEW_API_SMOKE", matches = "true")
class ReviewNewApiSmokeTest {

    @Test
    void shouldReviewSyntheticPdfThroughNewApi() throws Exception {
        byte[] pdf = syntheticPdf();
        StorageService storage = mock(StorageService.class);
        when(storage.download("synthetic-smoke.pdf"))
                .thenAnswer(ignored -> new ByteArrayInputStream(pdf));
        BasicReviewV1Properties properties = new BasicReviewV1Properties();
        properties.setRenderDpi(72);
        String gatewayUrl = System.getenv().getOrDefault("AI_GATEWAY_BASE_URL", "http://127.0.0.1:8090");
        HttpAiClient httpClient = new HttpAiClient(RestClient.builder().baseUrl(gatewayUrl).build());
        AtomicReference<AiChatResponse> capturedResponse = new AtomicReference<>();
        BasicReviewV1Workflow workflow = new BasicReviewV1Workflow(
                storage,
                new PdfPageRenderer(properties),
                request -> {
                    AiChatResponse response = httpClient.chat(request);
                    capturedResponse.set(response);
                    return response;
                },
                new ObjectMapper(),
                mock(ReviewTaskLogService.class));
        ReviewTask task = new ReviewTask();
        task.setId(1L);
        task.setAttemptNo(1);
        task.setWorkflowVersion(BasicReviewV1Workflow.VERSION_CODE);
        task.setPromptSnapshot(workflow.currentPrompt());
        SubmissionReviewDTO submission = new SubmissionReviewDTO(
                1L, 1L, 1L, 1, "synthetic-smoke.pdf");

        try {
            workflow.execute(task, submission);
        } catch (IllegalArgumentException ignored) {
            // 合成单页材料可能不满足业务评审质量门槛；S1 只验证调用和审计关联。
        }

        AiChatResponse response = capturedResponse.get();
        assertThat(response).isNotNull();
        assertThat(response.callId()).isNotBlank();
        assertThat(response.providerResponseId()).isNotBlank();
        assertThat(response.model()).isEqualTo("deepseek-v4-flash-vision-exp");
        System.out.printf("review-smoke callId=%s providerResponseIdPresent=true model=%s%n",
                response.callId(), response.model());
    }

    private byte[] syntheticPdf() throws Exception {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(72, 720);
                content.showText("Linear programming model with clear assumptions and sensitivity analysis.");
                content.endText();
            }
            document.save(output);
            return output.toByteArray();
        }
    }
}
