package com.leetmodel.knowledge.defense.dto;

import java.util.List;

/**
 * 服务端 4 道防线过滤与纠偏后的最终确定性结果。
 */
public record DefenseResult(
        List<String> selectedPaths,
        String reasoning,
        boolean fallbackTriggered,
        String fallbackReason) {

    public DefenseResult {
        if (selectedPaths == null) selectedPaths = List.of();
    }
}
