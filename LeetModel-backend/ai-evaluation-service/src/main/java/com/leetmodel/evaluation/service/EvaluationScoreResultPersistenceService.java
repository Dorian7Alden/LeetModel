package com.leetmodel.evaluation.service;

import com.leetmodel.evaluation.entity.EvaluationScoreResultItem;
import com.leetmodel.evaluation.mapper.EvaluationScoreResultItemMapper;
import com.leetmodel.evaluation.mapper.EvaluationScoreResultMapper;
import com.leetmodel.evaluation.mapper.EvaluationTaskMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 在任务行锁内为重算结果分配单调版本并追加保存。 */
@Service
@RequiredArgsConstructor
public class EvaluationScoreResultPersistenceService {

    private final EvaluationTaskMapper taskMapper;
    private final EvaluationScoreResultMapper resultMapper;
    private final EvaluationScoreResultItemMapper itemMapper;

    /**
     * 追加一版评分结果，不更新已有结果或任务原始指标。
     * @param bundle 待保存结果与逐项贡献
     */
    @Transactional
    public void append(EvaluationScoreResultService.ScoreBundle bundle) {
        Long taskId = bundle.result().getTaskId();
        if (taskMapper.lockById(taskId) == null) {
            throw new IllegalStateException("重新计算时评价任务不存在");
        }
        int nextVersion = resultMapper.selectMaxVersionNumber(taskId) + 1;
        bundle.result().setScoreResultVersion("SCORE_RESULT_V" + nextVersion);
        resultMapper.insert(bundle.result());
        for (EvaluationScoreResultItem item : bundle.items()) {
            item.setScoreResultId(bundle.result().getId());
            itemMapper.insert(item);
        }
    }
}
