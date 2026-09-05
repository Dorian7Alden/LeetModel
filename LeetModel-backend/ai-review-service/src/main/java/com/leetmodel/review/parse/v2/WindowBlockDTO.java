package com.leetmodel.review.parse.v2;

import java.util.List;

/**
 * 单个滑窗输出的内容块传输对象。
 *
 * <p>包含排版要素的分类载荷，不含全局 blockId（由平铺阶段统一编号）。</p>
 */
public record WindowBlockDTO(
        PaperDocumentV2.BlockType type,
        int physicalPage,
        String text,
        PaperDocumentV2.HeadingPayload heading,
        PaperDocumentV2.FormulaPayload formula,
        PaperDocumentV2.TablePayload table,
        PaperDocumentV2.FigurePayload figure,
        PaperDocumentV2.CodePayload code,
        List<PaperDocumentV2.ResourceReference> references
) {
    /**
     * 判断当前块是否为插图要素。
     *
     * @return 若类型为 FIGURE 返回 true
     */
    public boolean isFigure() {
        return type == PaperDocumentV2.BlockType.FIGURE;
    }

    /**
     * 将当前窗口切片块转换为已分配全局 blockId 的不可变内容块。
     *
     * @param blockId 全局唯一块标识（如 B1, B2）
     * @return 完整内容块对象
     */
    public PaperDocumentV2.ContentBlockV2 toContentBlock(String blockId) {
        return new PaperDocumentV2.ContentBlockV2(
                blockId,
                type,
                physicalPage,
                text,
                heading,
                formula,
                table,
                figure,
                code,
                references == null ? List.of() : List.copyOf(references)
        );
    }
}
