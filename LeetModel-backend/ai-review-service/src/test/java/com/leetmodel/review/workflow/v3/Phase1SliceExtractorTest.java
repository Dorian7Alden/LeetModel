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
}
