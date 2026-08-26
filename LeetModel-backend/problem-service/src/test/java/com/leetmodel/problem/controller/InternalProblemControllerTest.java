package com.leetmodel.problem.controller;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.leetmodel.problem.entity.Problem;
import com.leetmodel.problem.service.ProblemService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InternalProblemControllerTest {

    @Mock
    private ProblemService problemService;

    @Test
    void clampOptionLimitAndOnlyReturnMappedPublishedFacts() {
        Problem problem = new Problem();
        problem.setId(51001L);
        problem.setTitle("城市共享单车潮汐调度");
        problem.setContestId(2L);
        problem.setYear(2024);
        problem.setStatementLanguage("ZH");
        problem.setDifficulty(2);
        problem.setDurationMinutes(4320);
        when(problemService.list(org.mockito.ArgumentMatchers.<Wrapper<Problem>>any()))
                .thenReturn(List.of(problem));
        InternalProblemController controller = new InternalProblemController(problemService);

        var response = controller.getPublishedOptions("  单车  ", 500);

        assertEquals(1, response.getData().size());
        assertEquals(51001L, response.getData().get(0).getId());
        verify(problemService).list(org.mockito.ArgumentMatchers.<Wrapper<Problem>>any());
        assertEquals(50, InternalProblemController.normalizeLimit(500));
        assertEquals(1, InternalProblemController.normalizeLimit(0));
        assertEquals(20, InternalProblemController.normalizeLimit(null));
    }
}
