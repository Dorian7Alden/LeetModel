package com.leetmodel.review.parse;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 确定性的 PAPER_PARSE_V1 文本解析器；不伪造 OCR、公式或元素坐标。 */
@Component
public class PaperParseV1Parser {
    public static final String WORKFLOW_VERSION = "PAPER_PARSE_V1";
    public static final String SCHEMA_VERSION = "PAPER_DOCUMENT_V1";
    private static final Pattern HEADING = Pattern.compile(
            "^(?:第?[一二三四五六七八九十0-9]+[章节、.]|[0-9]+(?:\\.[0-9]+)*\\s+)(.{1,80})$");

    private final int maxPages;
    private final int maxCharacters;

    public PaperParseV1Parser(@Value("${review.parse.v1.max-pages:80}") int maxPages,
                              @Value("${review.parse.v1.max-characters:240000}") int maxCharacters) {
        this.maxPages = Math.max(1, maxPages);
        this.maxCharacters = Math.max(1000, maxCharacters);
    }

    public PaperDocumentV1 parse(Long submissionId, byte[] pdf) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            if (document.isEncrypted()) throw new IllegalArgumentException("PDF 已加密，无法解析");
            int totalPages = document.getNumberOfPages();
            if (totalPages < 1) throw new IllegalArgumentException("PDF 没有页面");
            int pagesToRead = Math.min(totalPages, maxPages);
            int characters = 0;
            int blankPages = 0;
            List<PaperDocumentV1.Page> pages = new ArrayList<>();
            List<PaperDocumentV1.Section> sections = new ArrayList<>();
            List<String> qualityWarnings = new ArrayList<>();
            PDFTextStripper stripper = new PDFTextStripper();
            boolean truncated = totalPages > pagesToRead;
            for (int page = 1; page <= pagesToRead; page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String text = normalize(stripper.getText(document));
                List<String> warnings = new ArrayList<>();
                if (text.isBlank()) {
                    blankPages++;
                    warnings.add("NO_EXTRACTABLE_TEXT");
                }
                int remaining = maxCharacters - characters;
                if (remaining <= 0) {
                    truncated = true;
                    break;
                }
                if (text.length() > remaining) {
                    text = text.substring(0, remaining);
                    warnings.add("PAGE_TEXT_TRUNCATED");
                    truncated = true;
                }
                characters += text.length();
                String blockId = "P" + page + "-B1";
                pages.add(new PaperDocumentV1.Page(page, blockId, text, "PDFBOX_TEXT",
                        !text.isBlank(), text.isBlank(), List.copyOf(warnings)));
                String heading = heading(text);
                if (heading != null) {
                    sections.add(new PaperDocumentV1.Section("SEC-" + (sections.size() + 1),
                            heading, 1, page, page, List.of(blockId)));
                }
            }
            if (pages.stream().noneMatch(PaperDocumentV1.Page::textAvailable)) {
                throw new IllegalArgumentException("PDF 没有可用文本，PAPER_PARSE_V1 不执行隐式 OCR");
            }
            if (truncated) qualityWarnings.add("DOCUMENT_TRUNCATED");
            if (blankPages > 0) qualityWarnings.add("BLANK_OR_SCANNED_PAGES_PRESENT");
            String status = blankPages == 0 && !truncated ? "SUCCESS" : "PARTIAL_SUCCESS";
            return new PaperDocumentV1(SCHEMA_VERSION, submissionId, totalPages, truncated,
                    List.copyOf(pages), closeSections(sections, pages.size()),
                    new PaperDocumentV1.Quality(status, pages.size() - blankPages,
                            blankPages, blankPages, List.copyOf(qualityWarnings)));
        }
    }

    private List<PaperDocumentV1.Section> closeSections(List<PaperDocumentV1.Section> input,
                                                        int lastParsedPage) {
        List<PaperDocumentV1.Section> result = new ArrayList<>();
        for (int index = 0; index < input.size(); index++) {
            PaperDocumentV1.Section section = input.get(index);
            int end = index + 1 < input.size()
                    ? input.get(index + 1).startPhysicalPage() - 1 : lastParsedPage;
            result.add(new PaperDocumentV1.Section(section.sectionId(), section.title(),
                    section.level(), section.startPhysicalPage(), Math.max(section.startPhysicalPage(), end),
                    section.blockIds()));
        }
        return List.copyOf(result);
    }

    private String heading(String text) {
        if (text == null) return null;
        for (String line : text.split("\\R")) {
            String value = line.trim();
            Matcher matcher = HEADING.matcher(value);
            if (matcher.matches()) return value.substring(0, Math.min(value.length(), 100));
        }
        return null;
    }

    private String normalize(String value) {
        return value == null ? "" : value.replace('\u0000', ' ')
                .replaceAll("[\\t\\x0B\\f\\r]+", " ")
                .replaceAll("[ ]{2,}", " ").trim();
    }
}
