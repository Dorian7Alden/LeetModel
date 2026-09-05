package com.leetmodel.review.parse.v2;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 基于 Apache PDFBox 的程序底层客观纯文本基准提取器。
 *
 * <p>用于重叠页分歧仲裁时提供客观文字事实参考，以及滑窗多次超时后的分块级降级兜底。</p>
 */
@Component
public class PdfBoxTextExtractor {

    /**
     * 提取指定 1-based 物理页的纯文本内容。
     *
     * @param document     PDF 文档对象
     * @param physicalPage 真实物理页码（从 1 递增）
     * @return 该物理页的底层纯文本字符串（空页返回空字符串）
     * @throws IOException 抽取异常
     */
    public String extractPageText(PDDocument document, int physicalPage) throws IOException {
        PDFTextStripper stripper = new PDFTextStripper();
        stripper.setStartPage(physicalPage);
        stripper.setEndPage(physicalPage);
        String raw = stripper.getText(document);
        return normalize(raw);
    }

    /**
     * 提取指定起止物理页区间范围的纯文本内容。
     *
     * @param document  PDF 文档对象
     * @param startPage 起始物理页码（包含）
     * @param endPage   结束物理页码（包含）
     * @return 纯文本合并字符串
     * @throws IOException 抽取异常
     */
    public String extractRangeText(PDDocument document, int startPage, int endPage) throws IOException {
        PDFTextStripper stripper = new PDFTextStripper();
        stripper.setStartPage(startPage);
        stripper.setEndPage(endPage);
        String raw = stripper.getText(document);
        return normalize(raw);
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.replace('\u0000', ' ')
                .replaceAll("[\\p{Cntrl}&&[^\\n\\r\\t]]", "")
                .trim();
    }
}
