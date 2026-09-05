package com.leetmodel.review.parse.v2;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 第二代 PDF 解析产物全局连贯平铺与组装器。
 *
 * <p>消除翻页断裂，按人类自然阅读顺序全局平铺内容块，统一赋予 B1, B2... 唯一标识，
 * 提取章节索引目录，量化全局美观度与排版紧凑度，组装不可变 PAPER_DOCUMENT_V2 领域实体。</p>
 */
@Slf4j
@Component
public class DocumentFlattener {

    private static final Pattern CHINESE_CHAR_PATTERN = Pattern.compile("[\\u4e00-\\u9fa5]");

    private final DiscrepancyArbiter arbiter;
    private final PdfBoxTextExtractor textExtractor;

    public DocumentFlattener(DiscrepancyArbiter arbiter,
                             PdfBoxTextExtractor textExtractor) {
        this.arbiter = arbiter;
        this.textExtractor = textExtractor;
    }

    /**
     * 将全量窗口提取切片与重叠页仲裁成果装配为连贯平铺的 PaperDocumentV2 实体。
     *
     * @param submissionId    提交记录 ID
     * @param contentSha256   PDF 内容 SHA-256 哈希
     * @param totalPages      总物理页数
     * @param completedChunks 按 windowIndex 升序排列的滑窗列表
     * @param document        已加载的 PDFBox 文档对象
     * @param hasDegradedOcr  全流程中是否触发过分块级 OCR 降级
     * @return 组装完成的不可变 PaperDocumentV2 实例
     */
    public PaperDocumentV2 flatten(Long submissionId,
                                   String contentSha256,
                                   int totalPages,
                                   List<WindowChunkDTO> completedChunks,
                                   PDDocument document,
                                   boolean hasDegradedOcr) {
        List<WindowBlockDTO> aggregatedBlocks = new ArrayList<>();

        if (totalPages == 1) {
            // 单页文档特化组装
            if (!completedChunks.isEmpty() && completedChunks.get(0).blocks() != null) {
                aggregatedBlocks.addAll(completedChunks.get(0).blocks());
            }
        } else {
            // 多页文档逐页收集与仲裁组装
            for (int page = 1; page <= totalPages; page++) {
                if (page == 1) {
                    // 第 1 页：取首个窗口 (1, 2) 中 physicalPage == 1 的内容
                    WindowChunkDTO firstChunk = completedChunks.get(0);
                    aggregatedBlocks.addAll(filterBlocksForPage(firstChunk, 1));
                } else if (page == totalPages) {
                    // 第 N 页：取末尾窗口 (N-1, N) 中 physicalPage == N 的内容
                    WindowChunkDTO lastChunk = completedChunks.get(completedChunks.size() - 1);
                    aggregatedBlocks.addAll(filterBlocksForPage(lastChunk, totalPages));
                } else {
                    // 中间第 2 ~ N-1 页：窗口 page-1 与 窗口 page 重叠覆盖
                    int prevWindowIdx = page - 1; // 0-based: page - 2
                    int currWindowIdx = page;     // 0-based: page - 1

                    WindowChunkDTO chunkA = completedChunks.get(prevWindowIdx - 1);
                    WindowChunkDTO chunkB = completedChunks.get(currWindowIdx - 1);

                    List<WindowBlockDTO> blocksA = filterBlocksForPage(chunkA, page);
                    List<WindowBlockDTO> blocksB = filterBlocksForPage(chunkB, page);

                    String localText = "";
                    try {
                        localText = textExtractor.extractPageText(document, page);
                    } catch (Exception ex) {
                        log.warn("提取第 {} 页本地文本异常: {}", page, ex.getMessage());
                    }

                    List<WindowBlockDTO> arbitrated = arbiter.arbitrate(
                            blocksA,
                            blocksB,
                            page,
                            localText,
                            submissionId
                    );
                    aggregatedBlocks.addAll(arbitrated);
                }
            }
        }

        // 赋予全局唯一递增 blockId: B1, B2...
        List<PaperDocumentV2.ContentBlockV2> finalBlocks = new ArrayList<>(aggregatedBlocks.size());
        List<PaperDocumentV2.SectionIndex> sections = new ArrayList<>();
        int blockSeq = 1;
        int sectionSeq = 1;
        int totalCharacters = 0;
        String detectedPaperTitle = null;
        Set<Integer> readablePages = new HashSet<>();

        for (WindowBlockDTO rawBlock : aggregatedBlocks) {
            if (rawBlock == null) continue;
            String blockId = "B" + (blockSeq++);
            PaperDocumentV2.ContentBlockV2 block = rawBlock.toContentBlock(blockId);
            finalBlocks.add(block);

            if (block.text() != null && !block.text().isBlank()) {
                totalCharacters += block.text().length();
                readablePages.add(block.physicalPage());
            }

            // 提取章节目录索引
            if (block.type() == PaperDocumentV2.BlockType.HEADING) {
                String title = block.heading() != null && block.heading().cleanTitle() != null
                        ? block.heading().cleanTitle()
                        : (block.text() != null ? block.text() : "章节");
                int level = block.heading() != null && block.heading().level() > 0
                        ? block.heading().level() : 1;
                sections.add(new PaperDocumentV2.SectionIndex(
                        "SEC-" + (sectionSeq++),
                        title,
                        level,
                        blockId,
                        block.physicalPage()
                ));
                if (detectedPaperTitle == null && level == 1) {
                    detectedPaperTitle = title;
                }
            }
        }

        if (detectedPaperTitle == null) {
            detectedPaperTitle = !sections.isEmpty() ? sections.get(0).title() : "数学建模论文";
        }

        // 汇总全局排版美观度与紧凑度
        PaperDocumentV2.LayoutAesthetics aesthetics = computeLayoutAesthetics(completedChunks);

        // 汇总全篇质量报告
        PaperDocumentV2.DocumentQualityV2 quality = computeQuality(
                finalBlocks,
                totalPages,
                readablePages.size(),
                hasDegradedOcr
        );

        // 元数据构建
        boolean hasChinese = CHINESE_CHAR_PATTERN.matcher(detectedPaperTitle).find();
        PaperDocumentV2.DocumentMetadata metadata = new PaperDocumentV2.DocumentMetadata(
                totalPages,
                totalCharacters,
                detectedPaperTitle,
                hasChinese ? "ZH" : "EN",
                PaperDocumentV2.WORKFLOW_VERSION,
                Instant.now().toString()
        );

        return new PaperDocumentV2(
                PaperDocumentV2.SCHEMA_VERSION,
                submissionId,
                contentSha256,
                metadata,
                aesthetics,
                List.copyOf(finalBlocks),
                List.copyOf(sections),
                quality
        );
    }

    private List<WindowBlockDTO> filterBlocksForPage(WindowChunkDTO chunk, int targetPage) {
        if (chunk == null || chunk.blocks() == null) {
            return List.of();
        }
        List<WindowBlockDTO> matched = new ArrayList<>();
        for (WindowBlockDTO block : chunk.blocks()) {
            if (block.physicalPage() == targetPage) {
                matched.add(block);
            }
        }
        // 容错：若未严格标出页码但处于该单页窗口中，兜底吸纳
        if (matched.isEmpty() && chunk.startPhysicalPage() == chunk.endPhysicalPage()
                && chunk.startPhysicalPage() == targetPage) {
            return chunk.blocks();
        }
        return matched;
    }

    private PaperDocumentV2.LayoutAesthetics computeLayoutAesthetics(List<WindowChunkDTO> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return new PaperDocumentV2.LayoutAesthetics(
                    80.0, "HIGH", "GOOD", "排版规整，未见大面积空白断层。"
            );
        }
        double sum = 0;
        int count = 0;
        boolean hasLow = false;
        boolean hasMedium = false;

        for (WindowChunkDTO chunk : chunks) {
            if (chunk.windowLayoutAesthetics() != null) {
                sum += chunk.windowLayoutAesthetics().score();
                count++;
                String compactness = chunk.windowLayoutAesthetics().pageCompactness();
                if ("LOW".equalsIgnoreCase(compactness)) hasLow = true;
                else if ("MEDIUM".equalsIgnoreCase(compactness)) hasMedium = true;
            }
        }

        double overallScore = count > 0 ? Math.round((sum / count) * 10.0) / 10.0 : 80.0;
        String compactness = hasLow ? "LOW" : (hasMedium ? "MEDIUM" : "HIGH");
        String typesettingQuality = overallScore >= 85 ? "EXCELLENT"
                : (overallScore >= 70 ? "GOOD" : "ACCEPTABLE");
        String comment = String.format(
                "全篇排版综合得分 %.1f，版面紧凑度为 %s，图文穿插均匀度为 %s。",
                overallScore, compactness, typesettingQuality
        );

        return new PaperDocumentV2.LayoutAesthetics(
                overallScore,
                compactness,
                typesettingQuality,
                comment
        );
    }

    private PaperDocumentV2.DocumentQualityV2 computeQuality(List<PaperDocumentV2.ContentBlockV2> blocks,
                                                           int totalPages,
                                                           int readablePagesCount,
                                                           boolean hasDegradedOcr) {
        int formulaCount = 0;
        int tableCount = 0;
        int figureCount = 0;
        double figureScoreSum = 0;

        for (PaperDocumentV2.ContentBlockV2 block : blocks) {
            if (block.type() == PaperDocumentV2.BlockType.FORMULA) {
                formulaCount++;
            } else if (block.type() == PaperDocumentV2.BlockType.TABLE) {
                tableCount++;
            } else if (block.type() == PaperDocumentV2.BlockType.FIGURE) {
                figureCount++;
                if (block.figure() != null) {
                    figureScoreSum += block.figure().aestheticScore();
                }
            }
        }

        double avgFigureScore = figureCount > 0
                ? Math.round((figureScoreSum / figureCount) * 10.0) / 10.0 : 0.0;
        int blankPages = Math.max(0, totalPages - readablePagesCount);

        List<String> warnings = new ArrayList<>();
        if (hasDegradedOcr) {
            warnings.add("DEGRADED_LOCAL_OCR_PRESENT");
        }
        if (blankPages > 0) {
            warnings.add("BLANK_OR_SCANNED_PAGES_PRESENT");
        }

        String status = hasDegradedOcr ? "PARTIAL_SUCCESS" : "SUCCESS";
        return new PaperDocumentV2.DocumentQualityV2(
                status,
                readablePagesCount,
                blankPages,
                formulaCount,
                tableCount,
                figureCount,
                avgFigureScore,
                List.copyOf(warnings)
        );
    }
}
