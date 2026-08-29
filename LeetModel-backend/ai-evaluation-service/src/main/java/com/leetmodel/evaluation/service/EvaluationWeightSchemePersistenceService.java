package com.leetmodel.evaluation.service;

import com.leetmodel.evaluation.entity.EvaluationWeightScheme;
import com.leetmodel.evaluation.entity.EvaluationWeightSchemeItem;
import com.leetmodel.evaluation.mapper.EvaluationWeightSchemeItemMapper;
import com.leetmodel.evaluation.mapper.EvaluationWeightSchemeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 以单一事务保存不可变权重方案及全部指标快照。 */
@Service
@RequiredArgsConstructor
public class EvaluationWeightSchemePersistenceService {

    private final EvaluationWeightSchemeMapper schemeMapper;
    private final EvaluationWeightSchemeItemMapper itemMapper;

    /**
     * 原子创建权重方案和指标配置。
     * @param scheme 权重方案
     * @param items 指标配置
     */
    @Transactional
    public void create(EvaluationWeightScheme scheme, List<EvaluationWeightSchemeItem> items) {
        schemeMapper.insert(scheme);
        for (EvaluationWeightSchemeItem item : items) {
            item.setSchemeId(scheme.getId());
            itemMapper.insert(item);
        }
    }
}
