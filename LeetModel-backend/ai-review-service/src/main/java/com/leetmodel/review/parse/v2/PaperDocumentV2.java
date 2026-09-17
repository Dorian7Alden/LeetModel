package com.leetmodel.review.parse.v2;

import java.util.List;

/**
 * 第二代论文 PDF 结构化文档产物模型（PAPER_DOCUMENT_V2）。
 *
 * <p>以自然阅读流全局平铺，以真实物理页（从 1 递增）做粗粒度首现归属，
 * 表格使用原生 HTML 表达，源码整块保真，包含插图长描述与排版美观度量化评分。</p>
 */
public record PaperDocumentV2(
        String schemaVersion,
        Long submissionId,
        String contentSha256,
        DocumentMetadata metadata,
        LayoutAesthetics layoutAesthetics,
        List<ContentBlockV2> blocks,
        List<SectionIndex> sections,
        DocumentQualityV2 quality
) {
    public static final String WORKFLOW_VERSION = "PAPER_PARSE_V2";
    public static final String SCHEMA_VERSION = "PAPER_DOCUMENT_V2";

    public record DocumentMetadata(
            int totalPages,
            int totalCharacters,
            String paperTitle,
            String detectedLanguage,
            String parseWorkflowVersion,
            String parsedAt
    ) {}

    public record LayoutAesthetics(
            double overallScore,
            String pageCompactness,
            String typesettingQuality,
            String comment
    ) {}

    public record ContentBlockV2(
            String blockId,
            BlockType type,
            int physicalPage,
            String text,
            HeadingPayload heading,
            FormulaPayload formula,
            TablePayload table,
            FigurePayload figure,
            CodePayload code,
            List<ResourceReference> references
    ) {}

    public enum BlockType {
        HEADING,
        PARAGRAPH,
        FORMULA,
        TABLE,
        FIGURE,
        CODE,
        LIST_ITEM
    }

    public record HeadingPayload(
            int level,
            String rawNumber,
            String cleanTitle
    ) {}

    public record FormulaPayload(
            String latex,
            String formulaNo,
            boolean isMultiLine
    ) {}

    public record TablePayload(
            String caption,
            String captionPosition,
            String tableNo,
            String html,
            String footnote
    ) {}

    public record FigurePayload(
            String caption,
            String captionPosition,
            String figureNo,
            String figureType,
            String description,
            double aestheticScore,
            String aestheticComment,
            List<SubFigure> subFigures
    ) {}

    public record SubFigure(
            String subNo,
            String subCaption,
            String subDescription
    ) {}

    public record CodePayload(
            String language,
            String codeContent
    ) {}

    public record ResourceReference(
            String targetType,
            String targetIdentifier,
            String rawText,
            boolean isSuperscript
    ) {}

    public record SectionIndex(
            String sectionId,
            String title,
            int level,
            String headingBlockId,
            int physicalPage
    ) {}

    public record DocumentQualityV2(
            String status,
            int readablePages,
            int blankPages,
            int formulaCount,
            int tableCount,
            int figureCount,
            double averageFigureScore,
            List<String> warnings
    ) {}
}
