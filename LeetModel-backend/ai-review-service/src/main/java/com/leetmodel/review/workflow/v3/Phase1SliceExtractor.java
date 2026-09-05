package com.leetmodel.review.workflow.v3;

import com.leetmodel.review.parse.v2.PaperDocumentV2;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 阶段一结构规范性评审切面抽取器。
 * 负责从 PAPER_DOCUMENT_V2 中轻量抽取摘要、问题分析、假设符号与排版元数据，
 * 将输入规模压缩至 8k~12k Tokens 内，免深度推理。
 */
@Component
public class Phase1SliceExtractor {

    public record Phase1ExtractedSlices(
            String abstractText,
            String problemAnalysisText,
            String assumptionNomenclatureText,
            String layoutAndCodeSummary,
            String consolidatedUserPrompt
    ) {}

    /**
     * 从第二代高保真文档中抽取阶段一全部切面。
     */
    public Phase1ExtractedSlices extract(PaperDocumentV2 document) {
        String abstractText = extractAbstract(document);
        String problemAnalysisText = extractProblemAnalysis(document);
        String assumptionNomenclatureText = extractAssumptionNomenclature(document);
        String layoutAndCodeSummary = extractLayoutAndCodeSummary(document);

        String consolidatedUserPrompt = """
                【切面一：论文摘要正文】
                %s

                【切面二：问题重述与分析章节】
                %s

                【切面三：模型基本假设与符号说明】
                %s

                【切面四：排版量化指标与附录代码概况】
                %s
                """.formatted(
                limitText(abstractText, 4000),
                limitText(problemAnalysisText, 6000),
                limitText(assumptionNomenclatureText, 6000),
                layoutAndCodeSummary
        );

        return new Phase1ExtractedSlices(
                abstractText,
                problemAnalysisText,
                assumptionNomenclatureText,
                layoutAndCodeSummary,
                consolidatedUserPrompt
        );
    }

    private String extractAbstract(PaperDocumentV2 document) {
        StringBuilder sb = new StringBuilder();
        List<PaperDocumentV2.SectionIndex> sections = document.sections();
        boolean foundSection = false;
        if (sections != null) {
            for (PaperDocumentV2.SectionIndex sec : sections) {
                String title = sec.title().toLowerCase(Locale.ROOT);
                if (title.contains("摘要") || title.contains("summary") || title.contains("abstract")) {
                    List<PaperDocumentV2.ContentBlockV2> blocks = getSectionBlocks(document, sec.headingBlockId());
                    for (var b : blocks) {
                        if (b.text() != null && !b.text().isBlank()) {
                            sb.append(b.text()).append("\n\n");
                        }
                    }
                    foundSection = true;
                    break;
                }
            }
        }
        if (!foundSection && document.blocks() != null) {
            for (var b : document.blocks()) {
                if (b.physicalPage() == 1 && b.type() == PaperDocumentV2.BlockType.PARAGRAPH) {
                    sb.append(b.text()).append("\n\n");
                }
            }
        }
        return sb.toString().trim();
    }

    private String extractProblemAnalysis(PaperDocumentV2 document) {
        StringBuilder sb = new StringBuilder();
        List<PaperDocumentV2.SectionIndex> sections = document.sections();
        if (sections != null) {
            for (PaperDocumentV2.SectionIndex sec : sections) {
                String title = sec.title();
                if (title.contains("重述") || title.contains("分析") || title.contains("背景") || title.contains("问题提出")) {
                    List<PaperDocumentV2.ContentBlockV2> blocks = getSectionBlocks(document, sec.headingBlockId());
                    for (var b : blocks) {
                        if (b.text() != null && !b.text().isBlank()) {
                            sb.append(b.text()).append("\n\n");
                        }
                    }
                }
            }
        }
        return sb.toString().trim();
    }

    private String extractAssumptionNomenclature(PaperDocumentV2 document) {
        StringBuilder sb = new StringBuilder();
        List<PaperDocumentV2.SectionIndex> sections = document.sections();
        if (sections != null) {
            for (PaperDocumentV2.SectionIndex sec : sections) {
                String title = sec.title();
                if (title.contains("假设") || title.contains("符号") || title.contains("说明") || title.contains("名词解释")) {
                    List<PaperDocumentV2.ContentBlockV2> blocks = getSectionBlocks(document, sec.headingBlockId());
                    for (var b : blocks) {
                        if (b.type() == PaperDocumentV2.BlockType.TABLE && b.table() != null) {
                            sb.append("[符号说明表格]\n").append(b.table().html()).append("\n\n");
                        } else if (b.text() != null && !b.text().isBlank()) {
                            sb.append(b.text()).append("\n\n");
                        }
                    }
                }
            }
        }
        return sb.toString().trim();
    }

    private String extractLayoutAndCodeSummary(PaperDocumentV2 document) {
        StringBuilder sb = new StringBuilder();
        if (document.layoutAesthetics() != null) {
            var la = document.layoutAesthetics();
            sb.append(String.format("- 排版美观度得分: %.1f\n", la.overallScore()));
            sb.append(String.format("- 页面紧凑度: %s\n", la.pageCompactness()));
            sb.append(String.format("- 排版质量等级: %s\n", la.typesettingQuality()));
            sb.append(String.format("- 排版评语: %s\n", la.comment()));
        }
        int codeBlockCount = 0;
        int codeLines = 0;
        List<String> languages = new ArrayList<>();
        if (document.blocks() != null) {
            for (var b : document.blocks()) {
                if (b.type() == PaperDocumentV2.BlockType.CODE && b.code() != null) {
                    codeBlockCount++;
                    String content = b.code().codeContent();
                    if (content != null) {
                        codeLines += content.split("\\R").length;
                    }
                    String lang = b.code().language();
                    if (lang != null && !languages.contains(lang)) {
                        languages.add(lang);
                    }
                }
            }
        }
        sb.append(String.format("- 附录代码块数量: %d 个，总行数约: %d 行\n", codeBlockCount, codeLines));
        sb.append(String.format("- 附录识别编程语言: %s\n", languages.isEmpty() ? "未识别" : String.join(", ", languages)));
        return sb.toString().trim();
    }

    private List<PaperDocumentV2.ContentBlockV2> getSectionBlocks(PaperDocumentV2 document, String headingBlockId) {
        List<PaperDocumentV2.ContentBlockV2> result = new ArrayList<>();
        List<PaperDocumentV2.ContentBlockV2> blocks = document.blocks();
        if (blocks == null) return result;
        boolean collecting = false;
        for (var b : blocks) {
            if (b.blockId().equals(headingBlockId)) {
                collecting = true;
                continue;
            }
            if (collecting) {
                if (b.type() == PaperDocumentV2.BlockType.HEADING) {
                    break;
                }
                result.add(b);
            }
        }
        return result;
    }

    private String limitText(String text, int max) {
        if (text == null) return "（无）";
        return text.length() <= max ? text : text.substring(0, max) + "\n...（超出部分已省略）";
    }
}
