package com.leetmodel.review.parse.v2;

import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

/**
 * 校验 PAPER_DOCUMENT_V2 是否具备进入评审链路的最低内容覆盖。
 *
 * <p>解析产物必须包含正文块、覆盖首尾页，并将缺失页控制在总页数的 5% 以内。
 * 不完整产物不得缓存复用或继续进入评分。</p>
 */
@Component
public class PaperParseV2QualityGate {

    /**
     * 校验解析文档的页级内容覆盖。
     *
     * @param document 待校验的 V2 解析文档，不能为 null
     * @throws IllegalStateException 当正文为空、首尾页缺失或整体缺页超过阈值
     */
    public void validate(PaperDocumentV2 document) {
        if (document == null || document.blocks() == null || document.blocks().isEmpty()) {
            throw new IllegalStateException("PAPER_PARSE_V2_EMPTY_CONTENT: 未提取到可评审的论文内容");
        }

        int totalPages = document.metadata() == null ? 0 : document.metadata().totalPages();
        if (totalPages < 1) {
            throw new IllegalStateException("PAPER_PARSE_V2_INVALID_PAGE_COUNT: 解析产物页数无效");
        }

        Set<Integer> coveredPages = collectCoveredPages(document, totalPages);
        int missingPages = totalPages - coveredPages.size();
        int allowedMissingPages = Math.max(1, totalPages / 20);
        boolean boundaryMissing = !coveredPages.contains(1) || !coveredPages.contains(totalPages);

        if (boundaryMissing || missingPages > allowedMissingPages) {
            throw new IllegalStateException(
                    "PAPER_PARSE_V2_INCOMPLETE_COVERAGE: coveredPages="
                            + coveredPages.size()
                            + "/"
                            + totalPages
                            + ", boundaryMissing="
                            + boundaryMissing
            );
        }
    }

    /**
     * 判断历史解析产物是否仍可安全复用。
     *
     * @param document 待检查的历史 V2 解析文档
     * @return 满足当前质量门时返回 true，否则返回 false
     */
    public boolean isReusable(PaperDocumentV2 document) {
        try {
            validate(document);
            return true;
        } catch (IllegalStateException exception) {
            return false;
        }
    }

    private Set<Integer> collectCoveredPages(
            PaperDocumentV2 document,
            int totalPages
    ) {
        Set<Integer> coveredPages = new HashSet<>();
        for (PaperDocumentV2.ContentBlockV2 block : document.blocks()) {
            if (block == null) continue;
            int physicalPage = block.physicalPage();
            if (physicalPage >= 1 && physicalPage <= totalPages) {
                coveredPages.add(physicalPage);
            }
        }
        return coveredPages;
    }
}
