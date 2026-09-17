package com.leetmodel.review.parse;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class PaperParseV1ParserTest {
    @Test
    void createsStablePhysicalPagesBlocksAndQuality() throws Exception {
        PaperParseV1Parser parser = new PaperParseV1Parser(20, 10000);

        PaperDocumentV1 result = parser.parse(101L, pdf("1 Model formulation", "2 Validation"));

        assertThat(result.schemaVersion()).isEqualTo("PAPER_DOCUMENT_V1");
        assertThat(result.pageCount()).isEqualTo(2);
        assertThat(result.pages()).extracting(PaperDocumentV1.Page::physicalPage)
                .containsExactly(1, 2);
        assertThat(result.pages()).extracting(PaperDocumentV1.Page::blockId)
                .containsExactly("P1-B1", "P2-B1");
        assertThat(result.quality().status()).isEqualTo("SUCCESS");
    }

    @Test
    void reportsBlankPageAsPartialInsteadOfInventingOcrText() throws Exception {
        PaperParseV1Parser parser = new PaperParseV1Parser(20, 10000);

        PaperDocumentV1 result = parser.parse(101L, pdf("Model", null));

        assertThat(result.quality().status()).isEqualTo("PARTIAL_SUCCESS");
        assertThat(result.quality().blankPages()).isEqualTo(1);
        assertThat(result.pages().get(1).extractionMethod()).isEqualTo("PDFBOX_TEXT");
        assertThat(result.pages().get(1).warnings()).contains("NO_EXTRACTABLE_TEXT");
    }

    private byte[] pdf(String... pages) throws Exception {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            for (String text : pages) {
                PDPage page = new PDPage();
                document.addPage(page);
                if (text != null) {
                    try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                        content.beginText();
                        content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                        content.newLineAtOffset(72, 720);
                        content.showText(text);
                        content.endText();
                    }
                }
            }
            document.save(output);
            return output.toByteArray();
        }
    }
}
