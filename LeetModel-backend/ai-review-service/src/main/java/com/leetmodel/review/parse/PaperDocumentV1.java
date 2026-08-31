package com.leetmodel.review.parse;

import java.util.List;

/** PAPER_DOCUMENT_V1：所有公共定位均使用从 1 开始的物理 PDF 页码。 */
public record PaperDocumentV1(
        String schemaVersion,
        Long submissionId,
        int pageCount,
        boolean truncated,
        List<Page> pages,
        List<Section> sections,
        Quality quality
) {
    public record Page(int physicalPage, String blockId, String text,
                       String extractionMethod, boolean textAvailable,
                       boolean scannedPage, List<String> warnings) {}

    public record Section(String sectionId, String title, int level,
                          int startPhysicalPage, int endPhysicalPage,
                          List<String> blockIds) {}

    public record Quality(String status, int readablePages, int blankPages,
                          int scannedPages, List<String> warnings) {}
}
