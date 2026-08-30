package com.leetmodel.admin.controller;

import com.leetmodel.admin.dto.AdminEvaluationScoreRecalculateDTO;
import com.leetmodel.admin.dto.AdminEvaluationWeightSchemeCreateDTO;
import com.leetmodel.admin.service.AdminFeignExecutor;
import com.leetmodel.common.api.dto.EvaluationDatasetCreateDTO;
import com.leetmodel.common.api.dto.EvaluationScoreRecalculateDTO;
import com.leetmodel.common.api.dto.EvaluationWeightSchemeCreateDTO;
import com.leetmodel.common.api.feign.AssistantFeignClient;
import com.leetmodel.common.api.feign.EvaluationFeignClient;
import com.leetmodel.common.api.feign.ReviewFeignClient;
import com.leetmodel.common.core.result.Result;
import com.leetmodel.common.security.context.UserContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminEvaluationControllerTest {

    @Mock private EvaluationFeignClient evaluationClient;
    @Mock private ReviewFeignClient reviewClient;
    @Mock private AssistantFeignClient assistantClient;

    private AdminEvaluationController controller;

    @BeforeEach
    void setUp() {
        controller = new AdminEvaluationController(evaluationClient, reviewClient, assistantClient,
                new AdminFeignExecutor());
    }

    @Test
    void createWeightSchemeMustInjectAuthenticatedAdministrator() {
        AdminEvaluationWeightSchemeCreateDTO request = new AdminEvaluationWeightSchemeCreateDTO();
        request.setSchemeCode("REVIEW_DEFAULT");
        request.setSchemeVersion("V1");
        request.setName("默认权重");
        request.setObjective("选择综合资源与稳定性表现更好的版本");
        request.setFeatureCode("REVIEW");
        request.setMetricSetVersion("REVIEW_METRICS_V1");
        request.setItems(List.of());
        when(evaluationClient.createWeightScheme(any())).thenReturn(Result.ok());

        try (MockedStatic<UserContext> userContext = mockStatic(UserContext.class)) {
            userContext.when(UserContext::getUserId).thenReturn(91L);
            controller.createWeightScheme(request);
        }

        ArgumentCaptor<EvaluationWeightSchemeCreateDTO> captor =
                ArgumentCaptor.forClass(EvaluationWeightSchemeCreateDTO.class);
        verify(evaluationClient).createWeightScheme(captor.capture());
        assertThat(captor.getValue().getCreatedBy()).isEqualTo(91L);
        assertThat(captor.getValue().getSchemeCode()).isEqualTo("REVIEW_DEFAULT");
    }

    @Test
    void recalculateMustInjectAuthenticatedAdministrator() {
        when(evaluationClient.recalculateScore(any(), any())).thenReturn(Result.ok());

        try (MockedStatic<UserContext> userContext = mockStatic(UserContext.class)) {
            userContext.when(UserContext::getUserId).thenReturn(92L);
            controller.recalculateScore(10L, new AdminEvaluationScoreRecalculateDTO(20L));
        }

        ArgumentCaptor<EvaluationScoreRecalculateDTO> captor =
                ArgumentCaptor.forClass(EvaluationScoreRecalculateDTO.class);
        verify(evaluationClient).recalculateScore(org.mockito.ArgumentMatchers.eq(10L),
                captor.capture());
        assertThat(captor.getValue().getWeightSchemeId()).isEqualTo(20L);
        assertThat(captor.getValue().getOperatorId()).isEqualTo(92L);
    }

    @Test
    void createDatasetMustOverwriteClaimedCreator() {
        EvaluationDatasetCreateDTO request = new EvaluationDatasetCreateDTO();
        request.setCreatedBy(999L);
        when(evaluationClient.createDataset(request)).thenReturn(Result.ok());

        try (MockedStatic<UserContext> userContext = mockStatic(UserContext.class)) {
            userContext.when(UserContext::getUserId).thenReturn(93L);
            controller.createDataset(request);
        }

        verify(evaluationClient).createDataset(request);
        assertThat(request.getCreatedBy()).isEqualTo(93L);
    }
}
