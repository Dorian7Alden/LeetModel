package com.leetmodel.suggestion.workflow;

import com.leetmodel.common.core.exception.BusinessException;
import com.leetmodel.suggestion.enums.SuggestionErrorCode;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 将 PDF 按页提取为带页码标记的文本，并限制输入体积。
 */
@Component
public class PdfTextExtractor {

    private final int maxPages;
    private final int maxCharacters;

    public PdfTextExtractor(
            @Value("${suggestion.v1.max-pages:40}") int maxPages,
            @Value("${suggestion.v1.max-characters:120000}") int maxCharacters) {
        this.maxPages = Math.max(1, maxPages);
        this.maxCharacters = Math.max(1000, maxCharacters);
    }

    /**
     * 提取 PDF 文本。
     *
     * @param pdf PDF 字节
     * @return 带页码文本、总页数和截断标记
     */
    public ExtractedPaper extract(byte[] pdf) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            int totalPages = document.getNumberOfPages();
            int pagesToRead = Math.min(totalPages, maxPages);
            PDFTextStripper stripper = new PDFTextStripper();
            StringBuilder text = new StringBuilder();
            boolean truncated = totalPages > pagesToRead;
            for (int page = 1; page <= pagesToRead; page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String pageText = stripper.getText(document).trim();
                if (!pageText.isBlank()) {
                    String block = "\n[第 " + page + " 页]\n" + pageText + "\n";
                    int remaining = maxCharacters - text.length();
                    if (remaining <= 0) {
                        truncated = true;
                        break;
                    }
                    if (block.length() > remaining) {
                        text.append(block, 0, remaining);
                        truncated = true;
                        break;
                    }
                    text.append(block);
                }
            }
            BusinessException.throwIf(text.toString().isBlank(), SuggestionErrorCode.PDF_TEXT_EMPTY);
            return new ExtractedPaper(text.toString(), totalPages, truncated);
        }
    }

    public record ExtractedPaper(String text, int pageCount, boolean truncated) {
    }
}
