package com.leetmodel.review.parse.v2;

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
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 重叠页一致性检测、插图独立融合与分歧仲裁器。
 *
 * <p>核心准则：插图要素解耦独立均值融合，文本要素快速比对放行，实质分歧结合本地 PDFBox 底层纯文本由轻量仲裁 AI 裁决。</p>
 */
@Slf4j
@Component
public class DiscrepancyArbiter {

    private static final double SIMILARITY_THRESHOLD = 0.85;

    private final AiClient aiClient;
    private final ObjectMapper objectMapper;
    private final PaperParseV2Properties properties;
    private final PaperParseV2ResponseParser responseParser;
    private final String arbiterPromptTemplate;

    public DiscrepancyArbiter(AiClient aiClient,
                              ObjectMapper objectMapper,
                              PaperParseV2Properties properties,
                              PaperParseV2ResponseParser responseParser) {
        this.aiClient = aiClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.responseParser = responseParser;
        this.arbiterPromptTemplate = loadArbiterPrompt();
    }

    /**
     * 对重叠物理页的两次解析结果进行一致性检测与融合仲裁。
     *
     * @param blocksA       前序滑窗输出的当前物理页切片
     * @param blocksB       后续滑窗输出的当前物理页切片
     * @param physicalPage  当前重叠物理页码
     * @param localText     本地 PDFBox 抽取的客观纯文本基准
     * @param submissionId  提交记录 ID
     * @return 最终定稿的内容块列表
     */
    public List<WindowBlockDTO> arbitrate(List<WindowBlockDTO> blocksA,
                                          List<WindowBlockDTO> blocksB,
                                          int physicalPage,
                                          String localText,
                                          Long submissionId) {
        List<WindowBlockDTO> safeA = blocksA == null ? List.of() : blocksA;
        List<WindowBlockDTO> safeB = blocksB == null ? List.of() : blocksB;

        // 1. 插图要素独立融合（排除在常规冲突判定之外，打分算术平均，长描述择优）
        List<WindowBlockDTO> figuresA = safeA.stream().filter(WindowBlockDTO::isFigure).toList();
        List<WindowBlockDTO> figuresB = safeB.stream().filter(WindowBlockDTO::isFigure).toList();
        List<WindowBlockDTO> mergedFigures = mergeFigures(figuresA, figuresB);

        // 2. 文本要素提取
        List<WindowBlockDTO> textA = safeA.stream().filter(b -> !b.isFigure()).toList();
        List<WindowBlockDTO> textB = safeB.stream().filter(b -> !b.isFigure()).toList();

        // 3. 文本要素一致性快速检测
        boolean consistent = isTextConsistent(textA, textB);
        List<WindowBlockDTO> finalTextBlocks;

        if (consistent) {
            // 快速一致路径：直接采纳后续窗口（视野向后延伸，更有利于接续）
            finalTextBlocks = textB.isEmpty() ? textA : textB;
        } else {
            // 仲裁路径：调用仲裁 AI
            finalTextBlocks = invokeArbitrationAi(textA, textB, physicalPage, localText, submissionId);
        }

        // 4. 合并最终文本块与已融合插图
        return combineBlocks(finalTextBlocks, mergedFigures);
    }

    /**
     * 判定文本要素是否在结构与语义上具有确定性一致。
     */
    public boolean isTextConsistent(List<WindowBlockDTO> listA, List<WindowBlockDTO> listB) {
        if (listA.isEmpty() && listB.isEmpty()) {
            return true;
        }
        if (listA.size() != listB.size()) {
            return false;
        }
        for (int i = 0; i < listA.size(); i++) {
            if (listA.get(i).type() != listB.get(i).type()) {
                return false;
            }
        }
        String textConcatA = extractFullText(listA);
        String textConcatB = extractFullText(listB);
        double similarity = computeSimilarity(textConcatA, textConcatB);
        return similarity >= SIMILARITY_THRESHOLD;
    }

    /**
     * 插图独立融合：按 figureNo 或 caption 匹配，打分取算术平均，描述取更详尽者。
     */
    public List<WindowBlockDTO> mergeFigures(List<WindowBlockDTO> listA, List<WindowBlockDTO> listB) {
        if (listA.isEmpty()) return listB;
        if (listB.isEmpty()) return listA;

        List<WindowBlockDTO> merged = new ArrayList<>();
        Set<Integer> matchedBIndices = new HashSet<>();

        for (WindowBlockDTO figA : listA) {
            int matchedIdx = -1;
            for (int j = 0; j < listB.size(); j++) {
                if (matchedBIndices.contains(j)) continue;
                WindowBlockDTO figB = listB.get(j);
                if (isSameFigure(figA, figB)) {
                    matchedIdx = j;
                    break;
                }
            }
            if (matchedIdx != -1) {
                matchedBIndices.add(matchedIdx);
                WindowBlockDTO figB = listB.get(matchedIdx);
                merged.add(fuseSingleFigure(figA, figB));
            } else {
                merged.add(figA);
            }
        }

        // 加入未匹配到的 B 中插图
        for (int j = 0; j < listB.size(); j++) {
            if (!matchedBIndices.contains(j)) {
                merged.add(listB.get(j));
            }
        }
        return List.copyOf(merged);
    }

    private WindowBlockDTO fuseSingleFigure(WindowBlockDTO a, WindowBlockDTO b) {
        PaperDocumentV2.FigurePayload payA = a.figure();
        PaperDocumentV2.FigurePayload payB = b.figure();
        if (payA == null) return b;
        if (payB == null) return a;

        double avgScore = (payA.aestheticScore() + payB.aestheticScore()) / 2.0;
        // 描述文本择优（字数更多者更详尽）
        String descA = payA.description() == null ? "" : payA.description();
        String descB = payB.description() == null ? "" : payB.description();
        String bestDesc = descA.length() >= descB.length() ? descA : descB;

        String commentA = payA.aestheticComment() == null ? "" : payA.aestheticComment();
        String commentB = payB.aestheticComment() == null ? "" : payB.aestheticComment();
        String bestComment = commentA.length() >= commentB.length() ? commentA : commentB;

        List<PaperDocumentV2.SubFigure> subFigures = payA.subFigures() != null && !payA.subFigures().isEmpty()
                ? payA.subFigures() : payB.subFigures();

        PaperDocumentV2.FigurePayload fused = new PaperDocumentV2.FigurePayload(
                payB.caption() != null ? payB.caption() : payA.caption(),
                payB.captionPosition() != null ? payB.captionPosition() : payA.captionPosition(),
                payB.figureNo() != null ? payB.figureNo() : payA.figureNo(),
                payB.figureType() != null ? payB.figureType() : payA.figureType(),
                bestDesc,
                avgScore,
                bestComment,
                subFigures
        );

        return new WindowBlockDTO(
                PaperDocumentV2.BlockType.FIGURE,
                a.physicalPage(),
                b.text() != null ? b.text() : a.text(),
                null,
                null,
                null,
                fused,
                null,
                b.references() != null && !b.references().isEmpty() ? b.references() : a.references()
        );
    }

    private boolean isSameFigure(WindowBlockDTO a, WindowBlockDTO b) {
        if (a.figure() == null || b.figure() == null) return false;
        String noA = a.figure().figureNo();
        String noB = b.figure().figureNo();
        if (noA != null && noB != null && !noA.isBlank() && noA.equalsIgnoreCase(noB)) {
            return true;
        }
        String capA = a.figure().caption();
        String capB = b.figure().caption();
        if (capA != null && capB != null && !capA.isBlank() && capA.equalsIgnoreCase(capB)) {
            return true;
        }
        return false;
    }

    private List<WindowBlockDTO> invokeArbitrationAi(List<WindowBlockDTO> textA,
                                                    List<WindowBlockDTO> textB,
                                                    int physicalPage,
                                                    String localText,
                                                    Long submissionId) {
        try {
            String jsonA = objectMapper.writeValueAsString(textA);
            String jsonB = objectMapper.writeValueAsString(textB);
            String safeLocal = localText == null || localText.isBlank() ? "（该页无程序抽取纯文本）" : localText;

            String prompt = arbiterPromptTemplate
                    .replace("[PHYSICAL_PAGE]", String.valueOf(physicalPage))
                    .replace("[VERSION_A_JSON]", jsonA)
                    .replace("[VERSION_B_JSON]", jsonB)
                    .replace("[LOCAL_PDFBOX_TEXT]", safeLocal);

            String businessTaskId = "submission:" + submissionId + ":page:" + physicalPage + ":arbitration";
            AiCallContext context = new AiCallContext(
                    "ai-review-service",
                    AiFeatureCode.PAPER_REVIEW,
                    AiOperationCode.FORMAL_REVIEW,
                    businessTaskId,
                    PaperDocumentV2.WORKFLOW_VERSION,
                    "PROMPT_PAPER_PARSE_ARBITER_0001",
                    properties.getArbiterModelConfigVersion(),
                    null,
                    AiCallPriority.P1,
                    "arbitration:" + businessTaskId,
                    Instant.now().plusSeconds(180)
            );

            AiChatRequest request = new AiChatRequest(
                    AiModality.TEXT,
                    context,
                    List.of(new AiMessage(AiRole.USER, List.of(new AiContentPart(AiContentType.TEXT, prompt, null)))),
                    4096,
                    0.1,
                    AiResponseFormat.JSON_OBJECT,
                    false
            );

            AiChatResponse response = aiClient.chat(request);
            if (response == null || response.content() == null || response.content().isBlank()) {
                log.warn("仲裁 AI 返回空内容，采用版本 A 兜底: submissionId={}, page={}", submissionId, physicalPage);
                return textA;
            }

            List<WindowBlockDTO> arbitrated = responseParser.parseArbiterBlocks(response.content());
            if (arbitrated == null || arbitrated.isEmpty()) {
                log.warn("仲裁 AI 解析产物为空，采用版本 A 兜底: submissionId={}, page={}", submissionId, physicalPage);
                return textA;
            }
            return arbitrated;
        } catch (Exception ex) {
            log.warn("仲裁 AI 调用失败，触发 Fail-Safe 回退版本 A: submissionId={}, page={}, error={}",
                    submissionId, physicalPage, ex.getMessage());
            return textA;
        }
    }

    private List<WindowBlockDTO> combineBlocks(List<WindowBlockDTO> textBlocks,
                                               List<WindowBlockDTO> figureBlocks) {
        List<WindowBlockDTO> result = new ArrayList<>(textBlocks.size() + figureBlocks.size());
        result.addAll(textBlocks);
        result.addAll(figureBlocks);
        return List.copyOf(result);
    }

    private String extractFullText(List<WindowBlockDTO> blocks) {
        StringBuilder builder = new StringBuilder();
        for (WindowBlockDTO block : blocks) {
            if (block.text() != null) {
                builder.append(block.text()).append(" ");
            }
        }
        return builder.toString().trim();
    }

    /**
     * 计算两段文本的字符 Bigram Jaccard 相似度。
     */
    public static double computeSimilarity(String textA, String textB) {
        if (textA == null && textB == null) return 1.0;
        if (textA == null || textB == null) return 0.0;
        String a = normalizeForComparison(textA);
        String b = normalizeForComparison(textB);
        if (a.equals(b)) return 1.0;
        if (a.isEmpty() || b.isEmpty()) return 0.0;

        Set<String> setA = bigrams(a);
        Set<String> setB = bigrams(b);
        if (setA.isEmpty() || setB.isEmpty()) {
            return a.equals(b) ? 1.0 : 0.0;
        }
        int intersection = 0;
        for (String bg : setA) {
            if (setB.contains(bg)) {
                intersection++;
            }
        }
        int union = setA.size() + setB.size() - intersection;
        return union == 0 ? 1.0 : (double) intersection / union;
    }

    private static String normalizeForComparison(String s) {
        return s.replaceAll("\\s+", "").toLowerCase();
    }

    private static Set<String> bigrams(String s) {
        Set<String> set = new HashSet<>();
        if (s.length() < 2) {
            set.add(s);
            return set;
        }
        for (int i = 0; i < s.length() - 1; i++) {
            set.add(s.substring(i, i + 2));
        }
        return set;
    }

    private String loadArbiterPrompt() {
        try {
            return new ClassPathResource("prompts/paper-parse-v2-arbiter.st")
                    .getContentAsString(StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalStateException("无法加载仲裁提示词模板 prompts/paper-parse-v2-arbiter.st", ex);
        }
    }
}
