package com.leetmodel.review.parse.v2;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leetmodel.common.ai.client.AiClient;
import com.leetmodel.common.ai.model.AiChatResponse;
import com.leetmodel.common.ai.model.AiProvider;
import com.leetmodel.review.entity.PaperParseChunkArtifact;
import com.leetmodel.review.mapper.PaperParseChunkArtifactMapper;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PaperParseV2ParserTest {

    private AiClient aiClient;
    private PaperParseChunkArtifactMapper chunkMapper;
    private PaperParseV2Parser parser;

    @BeforeEach
    void setUp() {
        aiClient = mock(AiClient.class);
        chunkMapper = mock(PaperParseChunkArtifactMapper.class);

        PaperParseV2Properties properties = new PaperParseV2Properties();
        properties.setRenderDpi(72);
        properties.setMaxRetries(1);
        properties.setRetryDelayMs(5L);

        ObjectMapper objectMapper = new ObjectMapper();
        PaperParseV2ResponseParser responseParser = new PaperParseV2ResponseParser(objectMapper);
        PdfPageRendererV2 pageRenderer = new PdfPageRendererV2(properties);
        PdfBoxTextExtractor textExtractor = new PdfBoxTextExtractor();

        DiscrepancyArbiter arbiter = new DiscrepancyArbiter(
                aiClient,
                objectMapper,
                properties,
                responseParser
        );

        DocumentFlattener flattener = new DocumentFlattener(arbiter, textExtractor);

        SlidingWindowScheduler scheduler = new SlidingWindowScheduler(
                aiClient,
                properties,
                responseParser,
                pageRenderer,
                textExtractor,
                chunkMapper,
                objectMapper
        );

        parser = new PaperParseV2Parser(properties, scheduler, flattener);
    }

    @Test
    void shouldParseTwoPagePdfEndToEnd() throws Exception {
        byte[] pdfBytes = createSyntheticPdf(2);

        String chunkResponse = """
                {
                  "windowIndex": 1,
                  "startPhysicalPage": 1,
                  "endPhysicalPage": 2,
                  "pageTopContinuation": false,
                  "pageBottomUnfinished": false,
                  "windowLayoutAesthetics": {
                    "score": 91.5,
                    "pageCompactness": "HIGH",
                    "comment": "全篇排版充实"
                  },
                  "blocks": [
                    {
                      "type": "HEADING",
                      "physicalPage": 1,
                      "text": "一、模型建立",
                      "heading": { "level": 1, "rawNumber": "一、", "cleanTitle": "模型建立" }
                    },
                    {
                      "type": "PARAGRAPH",
                      "physicalPage": 1,
                      "text": "无人机物流调度模型。",
                      "references": []
                    },
                    {
                      "type": "TABLE",
                      "physicalPage": 2,
                      "text": "表 1 参数",
                      "table": {
                        "caption": "表 1",
                        "captionPosition": "TOP",
                        "tableNo": "表 1",
                        "html": "<table border='1'><tr><th>参数</th><th>取值</th></tr><tr><td>v</td><td>20</td></tr></table>",
                        "footnote": ""
                      }
                    }
                  ]
                }
                """;

        when(aiClient.chat(any())).thenReturn(new AiChatResponse(
                "call-end-1", AiProvider.NEW_API, "vision", "p1", chunkResponse, null, "stop", null
        ));

        PaperDocumentV2 doc = parser.parse(4001L, pdfBytes, "sha256-mock-e2e");

        assertThat(doc).isNotNull();
        assertThat(doc.schemaVersion()).isEqualTo("PAPER_DOCUMENT_V2");
        assertThat(doc.submissionId()).isEqualTo(4001L);
        assertThat(doc.metadata().totalPages()).isEqualTo(2);
        assertThat(doc.metadata().paperTitle()).isEqualTo("模型建立");
        assertThat(doc.blocks()).hasSize(3);
        assertThat(doc.blocks().get(0).blockId()).isEqualTo("B1");
        assertThat(doc.blocks().get(1).blockId()).isEqualTo("B2");
        assertThat(doc.blocks().get(2).blockId()).isEqualTo("B3");
        assertThat(doc.blocks().get(2).type()).isEqualTo(PaperDocumentV2.BlockType.TABLE);
        assertThat(doc.quality().status()).isEqualTo("SUCCESS");
        assertThat(doc.quality().tableCount()).isEqualTo(1);
        assertThat(doc.layoutAesthetics().overallScore()).isEqualTo(91.5);
    }

    @Test
    void shouldRejectEncryptedPdf() throws Exception {
        byte[] encryptedBytes;
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            doc.addPage(new PDPage());
            StandardProtectionPolicy policy = new StandardProtectionPolicy("secret", "owner", new AccessPermission());
            doc.protect(policy);
            doc.save(out);
            encryptedBytes = out.toByteArray();
        }

        assertThrows(IllegalArgumentException.class, () ->
                parser.parse(4002L, encryptedBytes, "sha256-enc"));
    }

    private byte[] createSyntheticPdf(int pages) throws Exception {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (int i = 1; i <= pages; i++) {
                PDPage page = new PDPage();
                doc.addPage(page);
                try (PDPageContentStream stream = new PDPageContentStream(doc, page)) {
                    stream.beginText();
                    stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    stream.newLineAtOffset(50, 700);
                    stream.showText("Page " + i + " synthetic text content.");
                    stream.endText();
                }
            }
            doc.save(out);
            return out.toByteArray();
        }
    }
}
