package com.leetmodel.review.parse.v2;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 第二代 PDF 结构化视觉解析器（PAPER_PARSE_V2）。
 *
 * <p>基于双页滑窗推进、前文动态上下文注入、重叠冲突仲裁与全局平铺组装体系，
 * 输出高保真不可变 PAPER_DOCUMENT_V2 结构化产物。</p>
 */
@Slf4j
@Component
public class PaperParseV2Parser {

    public static final String WORKFLOW_VERSION = PaperDocumentV2.WORKFLOW_VERSION;
    public static final String SCHEMA_VERSION = PaperDocumentV2.SCHEMA_VERSION;

    private final PaperParseV2Properties properties;
    private final SlidingWindowScheduler scheduler;
    private final DocumentFlattener flattener;

    public PaperParseV2Parser(PaperParseV2Properties properties,
                              SlidingWindowScheduler scheduler,
                              DocumentFlattener flattener) {
        this.properties = properties;
        this.scheduler = scheduler;
        this.flattener = flattener;
    }

    /**
     * 解析提交的不可变 PDF 字节流为第二代结构化文档。
     *
     * @param submissionId  提交记录唯一标识
     * @param pdfBytes      不可变原始 PDF 字节数组
     * @param contentSha256 内容 SHA-256 校验和
     * @return 组装完成的 PaperDocumentV2 领域实体
     * @throws Exception 解析异常
     */
    public PaperDocumentV2 parse(Long submissionId, byte[] pdfBytes, String contentSha256) throws Exception {
        try {
            try (PDDocument document = Loader.loadPDF(pdfBytes)) {
                if (document.isEncrypted()) {
                    throw new IllegalArgumentException("PDF 已加密，无法解析");
                }
                int totalPages = document.getNumberOfPages();
                if (totalPages < 1) {
                    throw new IllegalArgumentException("PDF_EMPTY_NO_PAGES: PDF 没有页面");
                }

                int pagesToRead = Math.min(totalPages, properties.getMaxPages());
                log.info("启动 PAPER_PARSE_V2 解析流水线: submissionId={}, totalPages={}, pagesToRead={}",
                        submissionId, totalPages, pagesToRead);

                List<WindowChunkDTO> chunks = scheduler.schedule(submissionId, document, pagesToRead);

                boolean hasDegradedOcr = chunks.stream().anyMatch(chunk ->
                        chunk.windowLayoutAesthetics() != null
                                && chunk.windowLayoutAesthetics().comment() != null
                                && chunk.windowLayoutAesthetics().comment().contains("降级"));

                PaperDocumentV2 paperDocument = flattener.flatten(
                        submissionId,
                        contentSha256,
                        pagesToRead,
                        chunks,
                        document,
                        hasDegradedOcr
                );

                log.info("完成 PAPER_PARSE_V2 全局平铺组装: submissionId={}, blocks={}, sections={}, qualityStatus={}",
                        submissionId, paperDocument.blocks().size(), paperDocument.sections().size(),
                        paperDocument.quality().status());

                return paperDocument;
            }
        } catch (InvalidPasswordException ex) {
            throw new IllegalArgumentException("PDF 已加密，无法解析", ex);
        }
    }
}
