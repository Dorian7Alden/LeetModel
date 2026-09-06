package com.leetmodel.knowledge.defense;

import com.leetmodel.knowledge.defense.dto.CatalogSelectionResponse;
import com.leetmodel.knowledge.defense.dto.DefenseResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * AI 选拔服务端 4 道确定性防线协同引擎。
 * 1. 防御性 JSON 解析 (代码围栏清洗、闭包截取、LaTeX 反斜杠容错)
 * 2. 绝对白名单校验 (剔除幻觉与拼错路径)
 * 3. 数量硬截断 (封顶至 maxSelection)
 * 4. 全流程优雅降级 (异常或被完全过滤时回退基准文档)
 */
@Slf4j
@Component
public class CatalogSelectionDefenseEngine {

    private final DefensiveCatalogOutputParser outputParser;
    private final PathWhitelistValidator whitelistValidator;
    private final SelectionCountTruncator countTruncator;
    private final GracefulFallbackProvider fallbackProvider;

    public CatalogSelectionDefenseEngine(DefensiveCatalogOutputParser outputParser,
                                         PathWhitelistValidator whitelistValidator,
                                         SelectionCountTruncator countTruncator,
                                         GracefulFallbackProvider fallbackProvider) {
        this.outputParser = outputParser;
        this.whitelistValidator = whitelistValidator;
        this.countTruncator = countTruncator;
        this.fallbackProvider = fallbackProvider;
    }

    public DefenseResult defend(String rawLlmOutput, String category, int maxSelection, Set<String> validPaths) {
        // 防线 1: 防御性 JSON 解析
        CatalogSelectionResponse parsed;
        try {
            parsed = outputParser.parse(rawLlmOutput);
        } catch (Exception e) {
            log.warn("防线1触发失败熔断，转入防线4降级: error={}", e.getMessage());
            List<String> fallbacks = fallbackProvider.getFallbackPaths(category, validPaths);
            return new DefenseResult(fallbacks, "模型输出无法反序列化，已优雅降级至分类基准文档", true, e.getMessage());
        }

        // 防线 2: 绝对白名单校验
        List<String> whitelisted = whitelistValidator.validate(parsed.selectedPaths(), validPaths);

        // 防线 3: 数量硬截断
        List<String> truncated = countTruncator.truncate(whitelisted, maxSelection);

        // 防线 4: 判断是否需要优雅降级 (若被完全过滤为空)
        if (truncated.isEmpty()) {
            log.warn("防线2白名单过滤后有效路径为空，转入防线4降级");
            List<String> fallbacks = fallbackProvider.getFallbackPaths(category, validPaths);
            return new DefenseResult(fallbacks, "模型返回路径均不在受控白名单内，已优雅降级至分类基准文档", true, "WHITELIST_FILTERED_ALL");
        }

        return new DefenseResult(truncated, parsed.reasoning(), false, null);
    }

    public DefenseResult fallbackOnly(String category, Set<String> validPaths, String cause) {
        List<String> fallbacks = fallbackProvider.getFallbackPaths(category, validPaths);
        return new DefenseResult(fallbacks, "AI 模型调用异常或超时，已触发优雅降级", true, cause);
    }
}
