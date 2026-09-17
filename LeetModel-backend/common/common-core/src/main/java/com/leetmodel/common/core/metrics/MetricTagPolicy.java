package com.leetmodel.common.core.metrics;

import java.util.Locale;
import java.util.Set;

/**
 * Prometheus 低基数标签策略。
 *
 * <p>业务标识只能进入日志、Trace 或明细事实，不能进入时序标签。</p>
 */
public final class MetricTagPolicy {

    private static final Set<String> FORBIDDEN_ID_TAGS = Set.of(
            "userid", "teamid", "submissionid", "traceid", "swtraceid",
            "swspanid", "operationid", "eventid", "taskid", "domaintaskid",
            "evaluationtaskid", "attemptid", "attemptno", "aicallid", "callid",
            "messageid", "brokermessageid", "providerresponseid");

    private MetricTagPolicy() {
    }

    /**
     * 判定指定标签键是否属于严禁进入 Prometheus 的高基数业务标识。
     *
     * @param tagKey 待判定的指标 Tag 名称字符串
     * @return true 表示包含用户、题目、Trace 等高基数标识，必须拦截拒绝；false 表示允许放行
     */
    public static boolean isForbiddenIdTag(String tagKey) {
        if (tagKey == null || tagKey.isBlank()) return false;
        String normalized = tagKey.toLowerCase(Locale.ROOT)
                .replace("_", "")
                .replace("-", "")
                .replace(".", "");
        return FORBIDDEN_ID_TAGS.contains(normalized);
    }
}
