package com.leetmodel.suggestion.workflow;

import com.leetmodel.common.core.exception.BusinessException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PdfTextExtractorTest {

    @Test
    void extractsPageMarkersAndText() throws Exception {
        PdfTextExtractor extractor = new PdfTextExtractor(10, 5000);

        PdfTextExtractor.ExtractedPaper paper = extractor.extract(pdfWithText("Model validation"));

        assertThat(paper.pageCount()).isEqualTo(1);
        assertThat(paper.text()).contains("[第 1 页]").contains("Model validation");
        assertThat(paper.truncated()).isFalse();
    }

    @Test
    void rejectsPdfWithoutExtractableText() throws Exception {
        PdfTextExtractor extractor = new PdfTextExtractor(10, 5000);
        byte[] blank;
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            document.save(output);
            blank = output.toByteArray();
        }

        assertThatThrownBy(() -> extractor.extract(blank))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(40806);
    }

    private byte[] pdfWithText(String text) throws Exception {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(72, 720);
                content.showText(text);
                content.endText();
            }
            document.save(output);
            return output.toByteArray();
        }
    }
}
