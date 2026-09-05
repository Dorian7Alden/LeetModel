package com.leetmodel.review.parse.v2;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leetmodel.common.ai.client.AiClient;
import com.leetmodel.common.ai.model.AiChatRequest;
import com.leetmodel.common.ai.model.AiChatResponse;
import com.leetmodel.common.ai.model.AiProvider;
import com.leetmodel.review.entity.PaperParseChunkArtifact;
import com.leetmodel.review.mapper.PaperParseChunkArtifactMapper;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SlidingWindowSchedulerTest {

    private AiClient aiClient;
    private PaperParseV2Properties properties;
    private PaperParseV2ResponseParser responseParser;
    private PdfPageRendererV2 pageRenderer;
    private PdfBoxTextExtractor textExtractor;
    private PaperParseChunkArtifactMapper chunkMapper;
    private ObjectMapper objectMapper;
    private SlidingWindowScheduler scheduler;

    @BeforeEach
    void setUp() throws Exception {
        aiClient = mock(AiClient.class);
        properties = new PaperParseV2Properties();
        properties.setMaxRetries(1);
        properties.setRetryDelayMs(10L);
        objectMapper = new ObjectMapper();
        responseParser = new PaperParseV2ResponseParser(objectMapper);
        pageRenderer = mock(PdfPageRendererV2.class);
        textExtractor = mock(PdfBoxTextExtractor.class);
        chunkMapper = mock(PaperParseChunkArtifactMapper.class);

        when(pageRenderer.renderPageDataUrl(any(), anyInt())).thenReturn("data:image/jpeg;base64,mockimg");
        when(textExtractor.extractPageText(any(), anyInt())).thenReturn("本地抽取的正文文本");

        scheduler = new SlidingWindowScheduler(
                aiClient,
                properties,
                responseParser,
                pageRenderer,
                textExtractor,
                chunkMapper,
                objectMapper
        );
    }

    @Test
    void shouldScheduleSinglePageDocument() throws Exception {
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());

            String chunkResponse = """
                    {
                      "windowIndex": 1,
                      "startPhysicalPage": 1,
                      "endPhysicalPage": 1,
                      "pageTopContinuation": false,
                      "pageBottomUnfinished": false,
                      "windowLayoutAesthetics": {
                        "score": 85.0,
                        "pageCompactness": "HIGH",
                        "comment": "单页良好"
                      },
                      "blocks": [
                        {
                          "type": "PARAGRAPH",
                          "physicalPage": 1,
                          "text": "单页正文内容"
                        }
                      ]
                    }
                    """;

            when(aiClient.chat(any())).thenReturn(new AiChatResponse(
                    "call-1", AiProvider.NEW_API, "vision", "p1", chunkResponse, null, "stop", null
            ));

            List<WindowChunkDTO> chunks = scheduler.schedule(3001L, document, 1);
            assertThat(chunks).hasSize(1);
            assertThat(chunks.get(0).windowIndex()).isEqualTo(1);
            assertThat(chunks.get(0).blocks()).hasSize(1);
            verify(chunkMapper).insert(any(PaperParseChunkArtifact.class));
        }
    }

    @Test
    void shouldScheduleMultiPageWithDynamicContextAndSaveChunks() throws Exception {
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());
            document.addPage(new PDPage());
            document.addPage(new PDPage());

            String chunk1Response = """
                    {
                      "windowIndex": 1,
                      "startPhysicalPage": 1,
                      "endPhysicalPage": 2,
                      "pageTopContinuation": false,
                      "pageBottomUnfinished": true,
                      "windowLayoutAesthetics": {
                        "score": 90.0,
                        "pageCompactness": "HIGH",
                        "comment": "穿插良好"
                      },
                      "blocks": [
                        {
                          "type": "HEADING",
                          "physicalPage": 1,
                          "text": "一、引言",
                          "heading": { "level": 1, "rawNumber": "一、", "cleanTitle": "引言" }
                        },
                        {
                          "type": "PARAGRAPH",
                          "physicalPage": 2,
                          "text": "第一窗口结尾段落文本。"
                        }
                      ]
                    }
                    """;

            String chunk2Response = """
                    {
                      "windowIndex": 2,
                      "startPhysicalPage": 2,
                      "endPhysicalPage": 3,
                      "pageTopContinuation": true,
                      "pageBottomUnfinished": false,
                      "windowLayoutAesthetics": {
                        "score": 88.0,
                        "pageCompactness": "HIGH",
                        "comment": "充实"
                      },
                      "blocks": [
                        {
                          "type": "PARAGRAPH",
                          "physicalPage": 2,
                          "text": "第二窗口接续段落。"
                        },
                        {
                          "type": "PARAGRAPH",
                          "physicalPage": 3,
                          "text": "第三页终结正文。"
                        }
                      ]
                    }
                    """;

            when(aiClient.chat(any()))
                    .thenReturn(new AiChatResponse("call-w1", AiProvider.NEW_API, "v", "p", chunk1Response, null, "stop", null))
                    .thenReturn(new AiChatResponse("call-w2", AiProvider.NEW_API, "v", "p", chunk2Response, null, "stop", null));

            List<WindowChunkDTO> chunks = scheduler.schedule(3002L, document, 3);
            assertThat(chunks).hasSize(2);
            assertThat(chunks.get(0).windowIndex()).isEqualTo(1);
            assertThat(chunks.get(1).windowIndex()).isEqualTo(2);

            ArgumentCaptor<AiChatRequest> captor = ArgumentCaptor.forClass(AiChatRequest.class);
            verify(aiClient, times(2)).chat(captor.capture());

            // 验证第二个窗口的用户提示词注入了第一个窗口的大纲与尾部文本
            String secondPrompt = captor.getAllValues().get(1).messages().get(1).content().get(0).text();
            assertThat(secondPrompt)
                    .contains("引言")
                    .contains("第一窗口结尾段落文本");
        }
    }

    @Test
    void shouldResumeFromExistingSuccessfulChunkWithoutInvokingAi() throws Exception {
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());
            document.addPage(new PDPage());

            String cachedJson = """
                    {
                      "windowIndex": 1,
                      "startPhysicalPage": 1,
                      "endPhysicalPage": 2,
                      "pageTopContinuation": false,
                      "pageBottomUnfinished": false,
                      "windowLayoutAesthetics": { "score": 92.0, "pageCompactness": "HIGH", "comment": "已缓存" },
                      "blocks": [
                        { "type": "PARAGRAPH", "physicalPage": 1, "text": "缓存段落" }
                      ]
                    }
                    """;
            PaperParseChunkArtifact existing = new PaperParseChunkArtifact();
            existing.setSubmissionId(3003L);
            existing.setWindowIndex(1);
            existing.setStatus("SUCCESS");
            existing.setChunkJson(cachedJson);

            when(chunkMapper.selectOne(any())).thenReturn(existing);

            List<WindowChunkDTO> chunks = scheduler.schedule(3003L, document, 2);
            assertThat(chunks).hasSize(1);
            assertThat(chunks.get(0).blocks().get(0).text()).isEqualTo("缓存段落");
            verify(aiClient, never()).chat(any());
        }
    }

    @Test
    void shouldDegradeToLocalOcrWhenRetriesExhausted() throws Exception {
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());
            document.addPage(new PDPage());

            when(aiClient.chat(any())).thenThrow(new RuntimeException("网关超时 504"));
            when(textExtractor.extractPageText(any(), anyInt())).thenReturn("本地提取的兜底段落");

            List<WindowChunkDTO> chunks = scheduler.schedule(3004L, document, 2);
            assertThat(chunks).hasSize(1);
            WindowChunkDTO chunk = chunks.get(0);
            assertThat(chunk.windowLayoutAesthetics().comment()).contains("降级");
            assertThat(chunk.blocks()).isNotEmpty();
            assertThat(chunk.blocks().get(0).text()).isEqualTo("本地提取的兜底段落");
        }
    }
}
