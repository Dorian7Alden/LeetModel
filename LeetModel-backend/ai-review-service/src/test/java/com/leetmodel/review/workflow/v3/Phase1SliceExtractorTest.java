package com.leetmodel.review.workflow.v3;

import com.leetmodel.review.parse.v2.PaperDocumentV2;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class Phase1SliceExtractorTest {

    private final Phase1SliceExtractor extractor = new Phase1SliceExtractor();

    @Test
    void shouldExtractAllPhase1Slices() {
        PaperDocumentV2 doc = new PaperDocumentV2(
                PaperDocumentV2.SCHEMA_VERSION,
                1001L,
                "sha256-dummy",
                new PaperDocumentV2.DocumentMetadata(15, 12000, "测试数模论文", "ZH", "PAPER_PARSE_V2", "2026-09-05T12:00:00Z"),
                new PaperDocumentV2.LayoutAesthetics(88.5, "HIGH", "EXCELLENT", "排版紧凑整洁"),
                List.of(
                        new PaperDocumentV2.ContentBlockV2("B001", PaperDocumentV2.BlockType.HEADING, 1, "摘要",
                                new PaperDocumentV2.HeadingPayload(1, "", "摘要"), null, null, null, null, List.of()),
                        new PaperDocumentV2.ContentBlockV2("B002", PaperDocumentV2.BlockType.PARAGRAPH, 1, "针对问题一，本文建立了混合整数线性规划模型，得出最优总成本为32.4万元。", null, null, null, null, null, List.of()),
                        new PaperDocumentV2.ContentBlockV2("B003", PaperDocumentV2.BlockType.HEADING, 2, "一、问题重述与分析",
                                new PaperDocumentV2.HeadingPayload(1, "一、", "问题重述与分析"), null, null, null, null, List.of()),
                        new PaperDocumentV2.ContentBlockV2("B004", PaperDocumentV2.BlockType.PARAGRAPH, 2, "本文研究的是基于无人机配送的物流网络规划问题...", null, null, null, null, null, List.of()),
                        new PaperDocumentV2.ContentBlockV2("B005", PaperDocumentV2.BlockType.HEADING, 3, "二、模型基本假设与符号说明",
                                new PaperDocumentV2.HeadingPayload(1, "二、", "模型基本假设与符号说明"), null, null, null, null, List.of()),
                        new PaperDocumentV2.ContentBlockV2("B006", PaperDocumentV2.BlockType.PARAGRAPH, 3, "假设1：车辆速度恒定不变；假设2：忽略风力影响。", null, null, null, null, null, List.of()),
                        new PaperDocumentV2.ContentBlockV2("B007", PaperDocumentV2.BlockType.TABLE, 3, "符号说明表", null, null,
                                new PaperDocumentV2.TablePayload("表1 符号说明", "TOP", "1", "<table><tr><th>符号</th><th>含义</th><th>单位</th></tr><tr><td>c</td><td>成本</td><td>元</td></tr></table>", ""),
                                null, null, List.of()),
                        new PaperDocumentV2.ContentBlockV2("B008", PaperDocumentV2.BlockType.CODE, 15, "附录源码", null, null, null, null,
                                new PaperDocumentV2.CodePayload("python", "import numpy as np\nprint('hello world')"), List.of())
                ),
                List.of(
                        new PaperDocumentV2.SectionIndex("SEC_01", "摘要", 1, "B001", 1),
                        new PaperDocumentV2.SectionIndex("SEC_02", "一、问题重述与分析", 1, "B003", 2),
                        new PaperDocumentV2.SectionIndex("SEC_03", "二、模型基本假设与符号说明", 1, "B005", 3)
                ),
                new PaperDocumentV2.DocumentQualityV2("SUCCESS", 15, 0, 5, 2, 3, 85.0, List.of())
        );

        var slices = extractor.extract(doc);

        assertThat(slices.abstractText()).contains("最优总成本为32.4万元");
        assertThat(slices.problemAnalysisText()).contains("无人机配送的物流网络规划");
        assertThat(slices.assumptionNomenclatureText()).contains("假设1：车辆速度恒定不变");
        assertThat(slices.assumptionNomenclatureText()).contains("<table>");
        assertThat(slices.layoutAndCodeSummary()).contains("排版美观度得分: 88.5");
        assertThat(slices.layoutAndCodeSummary()).contains("附录代码块数量: 1 个");
        assertThat(slices.consolidatedUserPrompt()).contains("【切面一：论文摘要正文】");
    }

    @Test
    void shouldRecognizeEnglishSectionsAndKeepNestedSubsections() {
        PaperDocumentV2 doc = new PaperDocumentV2(
                PaperDocumentV2.SCHEMA_VERSION,
                1002L,
                "sha256-english",
                new PaperDocumentV2.DocumentMetadata(
                        6,
                        8000,
                        "Staircase Wear Analysis",
                        "EN",
                        "PAPER_PARSE_V2",
                        "2026-09-16T10:00:00Z"
                ),
                new PaperDocumentV2.LayoutAesthetics(
                        87.0,
                        "HIGH",
                        "EXCELLENT",
                        "Clean layout"
                ),
                List.of(
                        heading("B1", 1, 1, "Summary"),
                        paragraph("B2", 1, "The model reports an estimated daily traffic of 1925.5."),
                        heading("B3", 3, 2, "Problem Restatement and Analysis"),
                        paragraph("B4", 3, "We restate the archaeological questions and define the route."),
                        heading("B5", 3, 3, "Technical Route"),
                        paragraph("B6", 3, "The route combines wear volume and Bayesian inversion."),
                        heading("B7", 4, 2, "Notations"),
                        paragraph("B8", 4, "The primary notations are listed in Table 1."),
                        table("B9", 4),
                        heading("B10", 5, 2, "Assumptions"),
                        paragraph("B11", 5, "Assumption 1: walking positions follow a normal distribution."),
                        heading("B12", 6, 2, "Measurement Plan"),
                        paragraph("B13", 6, "Measurement details.")
                ),
                List.of(
                        new PaperDocumentV2.SectionIndex("SEC-1", "Summary", 1, "B1", 1),
                        new PaperDocumentV2.SectionIndex(
                                "SEC-2",
                                "Problem Restatement and Analysis",
                                2,
                                "B3",
                                3
                        ),
                        new PaperDocumentV2.SectionIndex("SEC-3", "Technical Route", 3, "B5", 3),
                        new PaperDocumentV2.SectionIndex("SEC-4", "Notations", 2, "B7", 4),
                        new PaperDocumentV2.SectionIndex("SEC-5", "Assumptions", 2, "B10", 5),
                        new PaperDocumentV2.SectionIndex("SEC-6", "Measurement Plan", 2, "B12", 6)
                ),
                new PaperDocumentV2.DocumentQualityV2(
                        "SUCCESS",
                        6,
                        0,
                        0,
                        1,
                        0,
                        0.0,
                        List.of()
                )
        );

        var slices = extractor.extract(doc);

        assertThat(slices.problemAnalysisText())
                .contains("archaeological questions")
                .contains("wear volume and Bayesian inversion");
        assertThat(slices.assumptionNomenclatureText())
                .contains("primary notations")
                .contains("<table>")
                .contains("walking positions follow a normal distribution");
    }

    private PaperDocumentV2.ContentBlockV2 heading(
            String blockId,
            int page,
            int level,
            String title
    ) {
        return new PaperDocumentV2.ContentBlockV2(
                blockId,
                PaperDocumentV2.BlockType.HEADING,
                page,
                title,
                new PaperDocumentV2.HeadingPayload(level, "", title),
                null,
                null,
                null,
                null,
                List.of()
        );
    }

    private PaperDocumentV2.ContentBlockV2 paragraph(
            String blockId,
            int page,
            String text
    ) {
        return new PaperDocumentV2.ContentBlockV2(
                blockId,
                PaperDocumentV2.BlockType.PARAGRAPH,
                page,
                text,
                null,
                null,
                null,
                null,
                null,
                List.of()
        );
    }

    private PaperDocumentV2.ContentBlockV2 table(String blockId, int page) {
        return new PaperDocumentV2.ContentBlockV2(
                blockId,
                PaperDocumentV2.BlockType.TABLE,
                page,
                "Table 1: Notations",
                null,
                null,
                new PaperDocumentV2.TablePayload(
                        "Table 1: Notations",
                        "TOP",
                        "1",
                        "<table><tr><th>Symbol</th><th>Meaning</th><th>Unit</th></tr></table>",
                        ""
                ),
                null,
                null,
                List.of()
        );
    }
}
