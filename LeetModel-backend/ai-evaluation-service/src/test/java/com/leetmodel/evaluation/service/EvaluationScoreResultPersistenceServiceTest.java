package com.leetmodel.evaluation.service;

import com.leetmodel.evaluation.entity.EvaluationScoreResult;
import com.leetmodel.evaluation.entity.EvaluationScoreResultItem;
import com.leetmodel.evaluation.mapper.EvaluationScoreResultItemMapper;
import com.leetmodel.evaluation.mapper.EvaluationScoreResultMapper;
import com.leetmodel.evaluation.mapper.EvaluationTaskMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EvaluationScoreResultPersistenceServiceTest {

    private final EvaluationTaskMapper taskMapper = mock(EvaluationTaskMapper.class);
    private final EvaluationScoreResultMapper resultMapper = mock(EvaluationScoreResultMapper.class);
    private final EvaluationScoreResultItemMapper itemMapper = mock(EvaluationScoreResultItemMapper.class);
    private final EvaluationScoreResultPersistenceService service =
            new EvaluationScoreResultPersistenceService(taskMapper, resultMapper, itemMapper);

    @Test
    void appendsMonotonicVersionUnderTaskRowLockWithoutUpdatingHistory() {
        EvaluationScoreResult result = new EvaluationScoreResult();
        result.setTaskId(20L);
        EvaluationScoreResultItem item = new EvaluationScoreResultItem();
        when(taskMapper.lockById(20L)).thenReturn(20L);
        when(resultMapper.selectMaxVersionNumber(20L)).thenReturn(1);
        doAnswer(invocation -> {
            invocation.<EvaluationScoreResult>getArgument(0).setId(902L);
            return 1;
        }).when(resultMapper).insert(result);

        service.append(new EvaluationScoreResultService.ScoreBundle(result, List.of(item)));

        assertThat(result.getScoreResultVersion()).isEqualTo("SCORE_RESULT_V2");
        assertThat(item.getScoreResultId()).isEqualTo(902L);
        verify(taskMapper).lockById(20L);
        verify(resultMapper).insert(result);
        verify(itemMapper).insert(item);
    }
}
