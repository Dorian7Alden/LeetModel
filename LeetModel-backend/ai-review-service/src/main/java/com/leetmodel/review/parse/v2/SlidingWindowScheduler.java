package com.leetmodel.review.parse.v2;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leetmodel.common.ai.client.AiClient;
import com.leetmodel.common.ai.model.AiCallContext;
import com.leetmodel.common.ai.model.AiCallPriority;
import com.leetmodel.common.ai.model.AiChatRequest;
import com.leetmodel.common.ai.model.AiChatResponse;
import com.leetmodel.common.ai.model.AiContentPart;
import com.leetmodel.common.ai.model.AiContentType;
import com.leetmodel.common.ai.model.AiFeatureCode;
import com.leetmodel.common.ai.model.AiMessage;
import com.leetmodel.common.ai.model.AiModality;
import com.leetmodel.common.ai.model.AiOperationCode;
import com.leetmodel.common.ai.model.AiResponseFormat;
import com.leetmodel.common.ai.model.AiRole;
import com.leetmodel.review.entity.PaperParseChunkArtifact;
import com.leetmodel.review.mapper.PaperParseChunkArtifactMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 第二代 PDF 解析双页滑窗调度器。
 *
 * <p>负责按步长为 1 推进滑动窗口，组装前文全局大纲与局部尾部文本动态参考，
 * 驱动视觉多模态大模型提取，支持分块中间存储持久化、断点续传与本地 OCR 兜底降级。</p>
 */
@Slf4j
@Component
public class SlidingWindowScheduler {

    private final AiClient aiClient;
    private final PaperParseV2Properties properties;
    private final PaperParseV2ResponseParser responseParser;
    private final PdfPageRendererV2 pageRenderer;
    private final PdfBoxTextExtractor textExtractor;
    private final PaperParseChunkArtifactMapper chunkMapper;
    private final ObjectMapper objectMapper;
    private final String systemPrompt;
    private final String userPromptTemplate;

    public SlidingWindowScheduler(AiClient aiClient,
                                  PaperParseV2Properties properties,
                                  PaperParseV2ResponseParser responseParser,
                                  PdfPageRendererV2 pageRenderer,
                                  PdfBoxTextExtractor textExtractor,
                                  PaperParseChunkArtifactMapper chunkMapper,
                                  ObjectMapper objectMapper) {
        this.aiClient = aiClient;
        this.properties = properties;
        this.responseParser = responseParser;
        this.pageRenderer = pageRenderer;
        this.textExtractor = textExtractor;
        this.chunkMapper = chunkMapper;
        this.objectMapper = objectMapper;
        this.systemPrompt = loadPrompt("prompts/paper-parse-v2-system.st");
        this.userPromptTemplate = loadPrompt("prompts/paper-parse-v2-user.st");
    }

    /**
     * 调度执行全篇文档的双页滑窗解析流程。
     *
     * @param submissionId 论文提交标识
     * @param document     PDFBox 加载的文档对象
     * @param totalPages   实际待解析总物理页数
     * @return 按 windowIndex 升序排列的成功或降级分块列表
     * @throws Exception 调度或异常错误
     */
    public List<WindowChunkDTO> schedule(Long submissionId,
                                         PDDocument document,
                                         int totalPages) throws Exception {
        if (totalPages < 1) {
            throw new IllegalArgumentException("PDF_EMPTY_NO_PAGES: PDF 没有页面");
        }

        // 1. 单页特化流 (N = 1)
        if (totalPages == 1) {
            return List.of(scheduleSinglePage(submissionId, document));
        }

        // 2. 常规多页滑窗流 (N >= 2)
        int totalWindows = totalPages - 1;
        List<WindowChunkDTO> completedChunks = new ArrayList<>(totalWindows);
        List<WindowBlockDTO> headingAccumulator = new ArrayList<>();
        String lastTailText = "（首页起始，无前文）";

        for (int windowIndex = 1; windowIndex <= totalWindows; windowIndex++) {
            int startPage = windowIndex;
            int endPage = windowIndex + 1;

            // 断点恢复检查：优先复用历史已成功落库的分块
            PaperParseChunkArtifact existing = findSuccessfulChunk(submissionId, windowIndex);
            if (existing != null && existing.getChunkJson() != null) {
                log.info("滑窗命中历史断点缓存复用: submissionId={}, windowIndex={}", submissionId, windowIndex);
                WindowChunkDTO cachedChunk = objectMapper.readValue(
                        existing.getChunkJson(),
                        WindowChunkDTO.class
                );
                completedChunks.add(cachedChunk);
                accumulateHeadings(headingAccumulator, cachedChunk);
                lastTailText = extractTailText(cachedChunk);
                continue;
            }

            // 动态组装用户提示词
            String outlineContext = buildOutlineContext(headingAccumulator);
            String userPrompt = userPromptTemplate
                    .replace("[WINDOW_INDEX]", String.valueOf(windowIndex))
                    .replace("[START_PHYSICAL_PAGE]", String.valueOf(startPage))
                    .replace("[END_PHYSICAL_PAGE]", String.valueOf(endPage))
                    .replace("[OUTLINE_CONTEXT]", outlineContext)
                    .replace("[TAIL_CONTEXT]", lastTailText);

            // 渲染双页高保真 JPEG Data URL
            String img1 = pageRenderer.renderPageDataUrl(document, startPage - 1);
            String img2 = pageRenderer.renderPageDataUrl(document, endPage - 1);
            List<String> pageImages = List.of(img1, img2);

            // 带局部重试与 OCR 降级调度的多模态执行
            WindowChunkDTO chunk = executeWindowWithRetryAndFallback(
                    submissionId,
                    document,
                    windowIndex,
                    startPage,
                    endPage,
                    pageImages,
                    userPrompt
            );

            completedChunks.add(chunk);
            accumulateHeadings(headingAccumulator, chunk);
            lastTailText = extractTailText(chunk);
        }

        return completedChunks;
    }

    private WindowChunkDTO scheduleSinglePage(Long submissionId, PDDocument document) throws Exception {
        PaperParseChunkArtifact existing = findSuccessfulChunk(submissionId, 1);
        if (existing != null && existing.getChunkJson() != null) {
            return objectMapper.readValue(existing.getChunkJson(), WindowChunkDTO.class);
        }

        String userPrompt = userPromptTemplate
                .replace("[WINDOW_INDEX]", "1")
                .replace("[START_PHYSICAL_PAGE]", "1")
                .replace("[END_PHYSICAL_PAGE]", "1")
                .replace("[OUTLINE_CONTEXT]", "（单页文档，暂无前文大纲）")
                .replace("[TAIL_CONTEXT]", "（首页起始，无前文）");

        String img = pageRenderer.renderPageDataUrl(document, 0);
        return executeWindowWithRetryAndFallback(
                submissionId,
                document,
                1,
                1,
                1,
                List.of(img),
                userPrompt
        );
    }

    private WindowChunkDTO executeWindowWithRetryAndFallback(Long submissionId,
                                                             PDDocument document,
                                                             int windowIndex,
                                                             int startPage,
                                                             int endPage,
                                                             List<String> pageImages,
                                                             String userPrompt) {
        int maxRetries = Math.max(0, properties.getMaxRetries());
        long delay = properties.getRetryDelayMs();
        Exception lastError = null;

        for (int attempt = 1; attempt <= maxRetries + 1; attempt++) {
            try {
                WindowChunkDTO chunk = invokeMultimodalModel(
                        submissionId,
                        windowIndex,
                        startPage,
                        endPage,
                        pageImages,
                        userPrompt,
                        attempt
                );
                saveOrUpdateChunk(
                        submissionId,
                        windowIndex,
                        startPage,
                        endPage,
                        "SUCCESS",
                        chunk,
                        attempt,
                        null
                );
                return chunk;
            } catch (Exception ex) {
                lastError = ex;
                log.warn("滑窗多模态调用第 {} 次失败: submissionId={}, windowIndex={}, error={}",
                        attempt, submissionId, windowIndex, ex.getMessage());
                if (attempt <= maxRetries) {
                    try {
                        Thread.sleep(delay * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        // 多次重试仍失败：触发分块级本地纯文本 OCR 兜底
        log.error("滑窗达到最大重试次数，触发本地 OCR 降级兜底: submissionId={}, windowIndex={}",
                submissionId, windowIndex, lastError);
        WindowChunkDTO fallbackChunk = createOcrFallbackChunk(
                document,
                windowIndex,
                startPage,
                endPage
        );
        saveOrUpdateChunk(
                submissionId,
                windowIndex,
                startPage,
                endPage,
                "DEGRADED_OCR",
                fallbackChunk,
                maxRetries + 1,
                lastError != null ? lastError.getMessage() : "滑窗重试耗尽降级"
        );
        return fallbackChunk;
    }

    private WindowChunkDTO invokeMultimodalModel(Long submissionId,
                                                 int windowIndex,
                                                 int startPage,
                                                 int endPage,
                                                 List<String> pageImages,
                                                 String userPrompt,
                                                 int attempt) {
        List<AiContentPart> userParts = new ArrayList<>();
        userParts.add(new AiContentPart(AiContentType.TEXT, userPrompt, null));
        for (String url : pageImages) {
            userParts.add(new AiContentPart(AiContentType.IMAGE_URL, null, url));
        }

        AiMessage sysMsg = new AiMessage(
                AiRole.SYSTEM,
                List.of(new AiContentPart(AiContentType.TEXT, systemPrompt, null))
        );
        AiMessage usrMsg = new AiMessage(AiRole.USER, userParts);

        String businessTaskId = "submission:" + submissionId + ":window:" + windowIndex;
        AiCallContext context = new AiCallContext(
                "ai-review-service",
                AiFeatureCode.PAPER_REVIEW,
                AiOperationCode.FORMAL_REVIEW,
                businessTaskId,
                PaperDocumentV2.WORKFLOW_VERSION,
                "PROMPT_PAPER_PARSE_V2_0001",
                properties.getVisionModelConfigVersion(),
                null,
                AiCallPriority.P1,
                "parse-window:" + businessTaskId + ":attempt:" + attempt,
                Instant.now().plusSeconds(300)
        );

        AiChatRequest request = new AiChatRequest(
                AiModality.MULTIMODAL,
                context,
                List.of(sysMsg, usrMsg),
                4096,
                0.1,
                AiResponseFormat.JSON_OBJECT,
                false
        );

        AiChatResponse response = aiClient.chat(request);
        if (response == null || response.content() == null || response.content().isBlank()) {
            throw new IllegalArgumentException("AI 网关未返回滑窗解析内容: windowIndex=" + windowIndex);
        }

        return responseParser.parseWindowChunk(response.content());
    }

    private WindowChunkDTO createOcrFallbackChunk(PDDocument document,
                                                 int windowIndex,
                                                 int startPage,
                                                 int endPage) {
        List<WindowBlockDTO> blocks = new ArrayList<>();
        try {
            String text1 = textExtractor.extractPageText(document, startPage);
            if (!text1.isBlank()) {
                blocks.add(new WindowBlockDTO(
                        PaperDocumentV2.BlockType.PARAGRAPH,
                        startPage,
                        text1,
                        null, null, null, null, null, List.of()
                ));
            }
            if (startPage != endPage) {
                String text2 = textExtractor.extractPageText(document, endPage);
                if (!text2.isBlank()) {
                    blocks.add(new WindowBlockDTO(
                            PaperDocumentV2.BlockType.PARAGRAPH,
                            endPage,
                            text2,
                            null, null, null, null, null, List.of()
                    ));
                }
            }
        } catch (Exception ex) {
            log.warn("本地文本提取异常: page={}-{}, error={}", startPage, endPage, ex.getMessage());
        }

        WindowChunkDTO.WindowLayoutAesthetics aesthetics = new WindowChunkDTO.WindowLayoutAesthetics(
                60.0,
                "MEDIUM",
                "多模态视觉解析多次超时，自动触发分块级本地纯文本降级兜底"
        );

        return new WindowChunkDTO(
                windowIndex,
                startPage,
                endPage,
                false,
                false,
                aesthetics,
                blocks
        );
    }

    private PaperParseChunkArtifact findSuccessfulChunk(Long submissionId, int windowIndex) {
        return chunkMapper.selectOne(new LambdaQueryWrapper<PaperParseChunkArtifact>()
                .eq(PaperParseChunkArtifact::getSubmissionId, submissionId)
                .eq(PaperParseChunkArtifact::getWorkflowVersion, PaperDocumentV2.WORKFLOW_VERSION)
                .eq(PaperParseChunkArtifact::getWindowIndex, windowIndex)
                .eq(PaperParseChunkArtifact::getStatus, "SUCCESS")
                .last("LIMIT 1"));
    }

    private void saveOrUpdateChunk(Long submissionId,
                                   int windowIndex,
                                   int startPage,
                                   int endPage,
                                   String status,
                                   WindowChunkDTO chunk,
                                   int attemptNo,
                                   String errorMessage) {
        try {
            PaperParseChunkArtifact existing = chunkMapper.selectOne(
                    new LambdaQueryWrapper<PaperParseChunkArtifact>()
                            .eq(PaperParseChunkArtifact::getSubmissionId, submissionId)
                            .eq(PaperParseChunkArtifact::getWorkflowVersion, PaperDocumentV2.WORKFLOW_VERSION)
                            .eq(PaperParseChunkArtifact::getWindowIndex, windowIndex)
                            .last("LIMIT 1")
            );
            String json = chunk != null ? objectMapper.writeValueAsString(chunk) : null;
            if (existing == null) {
                PaperParseChunkArtifact artifact = new PaperParseChunkArtifact();
                artifact.setSubmissionId(submissionId);
                artifact.setWorkflowVersion(PaperDocumentV2.WORKFLOW_VERSION);
                artifact.setWindowIndex(windowIndex);
                artifact.setStartPage(startPage);
                artifact.setEndPage(endPage);
                artifact.setStatus(status);
                artifact.setChunkJson(json);
                artifact.setAttemptNo(attemptNo);
                artifact.setErrorMessage(errorMessage);
                chunkMapper.insert(artifact);
            } else {
                existing.setStatus(status);
                existing.setChunkJson(json);
                existing.setAttemptNo(attemptNo);
                existing.setErrorMessage(errorMessage);
                chunkMapper.updateById(existing);
            }
        } catch (Exception ex) {
            log.warn("保存滑窗中间切片异常: submissionId={}, windowIndex={}, error={}",
                    submissionId, windowIndex, ex.getMessage());
        }
    }

    private void accumulateHeadings(List<WindowBlockDTO> accumulator, WindowChunkDTO chunk) {
        if (chunk == null || chunk.blocks() == null) return;
        for (WindowBlockDTO block : chunk.blocks()) {
            if (block.type() == PaperDocumentV2.BlockType.HEADING) {
                accumulator.add(block);
            }
        }
    }

    private String buildOutlineContext(List<WindowBlockDTO> headings) {
        if (headings == null || headings.isEmpty()) {
            return "（暂无前文大纲）";
        }
        StringBuilder builder = new StringBuilder();
        for (WindowBlockDTO h : headings) {
            int level = h.heading() != null && h.heading().level() > 0 ? h.heading().level() : 1;
            String indent = "  ".repeat(Math.max(0, level - 1));
            String title = h.text() != null ? h.text()
                    : (h.heading() != null ? h.heading().cleanTitle() : "章节");
            builder.append(indent).append("- ").append(title).append(" (第 ")
                    .append(h.physicalPage()).append(" 页)\n");
        }
        return builder.toString().trim();
    }

    private String extractTailText(WindowChunkDTO chunk) {
        if (chunk == null || chunk.blocks() == null || chunk.blocks().isEmpty()) {
            return "（首页起始，无前文）";
        }
        List<WindowBlockDTO> blocks = chunk.blocks();
        for (int i = blocks.size() - 1; i >= 0; i--) {
            WindowBlockDTO block = blocks.get(i);
            String text = block.text();
            if (text != null && !text.isBlank()) {
                String trimmed = text.trim();
                if (trimmed.length() <= 400) {
                    return trimmed;
                }
                return "..." + trimmed.substring(trimmed.length() - 400);
            }
        }
        return "（首页起始，无前文）";
    }

    private String loadPrompt(String path) {
        try {
            return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalStateException("无法加载提示词模板: " + path, ex);
        }
    }
}
