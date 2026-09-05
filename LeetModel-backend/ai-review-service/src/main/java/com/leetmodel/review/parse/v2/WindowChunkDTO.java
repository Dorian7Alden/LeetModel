package com.leetmodel.review.parse.v2;

import java.util.List;

/**
 * 单个滑窗（双页或单页特化）由视觉模型或降级流程提取的中间分块传输对象。
 *
 * <p>反映单次窗口的物理页范围、翻页接续特征、窗口排版美观度与内容块列表。</p>
 */
public record WindowChunkDTO(
        int windowIndex,
        int startPhysicalPage,
        int endPhysicalPage,
        boolean pageTopContinuation,
        boolean pageBottomUnfinished,
        WindowLayoutAesthetics windowLayoutAesthetics,
        List<WindowBlockDTO> blocks
) {
    public record WindowLayoutAesthetics(
            double score,
            String pageCompactness,
            String comment
    ) {}
}
