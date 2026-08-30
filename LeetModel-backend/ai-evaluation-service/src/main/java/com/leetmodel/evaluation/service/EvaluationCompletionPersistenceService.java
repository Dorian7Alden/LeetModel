package com.leetmodel.evaluation.service;

import com.leetmodel.evaluation.entity.EvaluationScoreResultItem;
import com.leetmodel.evaluation.entity.EvaluationTask;
import com.leetmodel.evaluation.mapper.EvaluationScoreResultItemMapper;
import com.leetmodel.evaluation.mapper.EvaluationScoreResultMapper;
import com.leetmodel.evaluation.mapper.EvaluationTaskMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/** 原子保存任务完成事实、原始指标和首版版本选择指数。 */
@Service
@RequiredArgsConstructor
public class EvaluationCompletionPersistenceService {

    private final EvaluationTaskMapper taskMapper;
    private final EvaluationScoreResultMapper resultMapper;
    private final EvaluationScoreResultItemMapper itemMapper;

    /**
     * 仅由未完成状态执行一次任务完成与评分持久化。
     * @param task 评价任务
     * @param terminal 终态槽位数
     * @param failed 失败槽位数
     * @param metrics 旧兼容字段与可信原始指标
     * @param rawMetricsJson 原始指标 JSON
     * @param scoreBundle 首版评分结果
     * @param now 完成时间
     * @return 是否由本次调用完成
     */
    @Transactional
    public boolean complete(EvaluationTask task,
                            int terminal,
                            int failed,
                            EvaluationMetricsCalculator.Metrics metrics,
                            String rawMetricsJson,
                            EvaluationScoreResultService.ScoreBundle scoreBundle,
                            LocalDateTime now) {
        int updated = taskMapper.complete(task.getId(), terminal, failed, metrics.validityScore(),
                metrics.stabilityScore(), metrics.successRate(), metrics.latencyScore(),
                metrics.overallScore(), metrics.averageDurationMs(), rawMetricsJson, now);
        if (updated == 0) return false;
        resultMapper.insert(scoreBundle.result());
        for (EvaluationScoreResultItem item : scoreBundle.items()) {
            item.setScoreResultId(scoreBundle.result().getId());
            itemMapper.insert(item);
        }
        return true;
    }
}
