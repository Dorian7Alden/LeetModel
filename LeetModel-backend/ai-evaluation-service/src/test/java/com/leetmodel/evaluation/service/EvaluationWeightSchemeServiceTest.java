package com.leetmodel.evaluation.service;

import com.leetmodel.common.api.dto.EvaluationWeightItemCreateDTO;
import com.leetmodel.common.api.dto.EvaluationWeightSchemeCreateDTO;
import com.leetmodel.common.core.exception.BusinessException;
import com.leetmodel.evaluation.entity.EvaluationWeightScheme;
import com.leetmodel.evaluation.entity.EvaluationWeightSchemeItem;
import com.leetmodel.evaluation.mapper.EvaluationWeightSchemeItemMapper;
import com.leetmodel.evaluation.mapper.EvaluationWeightSchemeMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EvaluationWeightSchemeServiceTest {

    private final EvaluationWeightSchemePersistenceService persistenceService =
            mock(EvaluationWeightSchemePersistenceService.class);
    private final EvaluationWeightSchemeMapper schemeMapper = mock(EvaluationWeightSchemeMapper.class);
    private final EvaluationWeightSchemeItemMapper itemMapper = mock(EvaluationWeightSchemeItemMapper.class);
    private final EvaluationWeightSchemeService service = new EvaluationWeightSchemeService(
            new EvaluationMetricRegistry(), persistenceService, schemeMapper, itemMapper);

    @Test
    @SuppressWarnings("unchecked")
    void createsImmutableCompatibleSchemeWhoseWeightsEqualOneHundredPercent() {
        EvaluationWeightSchemeCreateDTO request = request("REVIEW", new BigDecimal("60.0000"),
                new BigDecimal("40.0000"));
        doAnswer(invocation -> {
            EvaluationWeightScheme scheme = invocation.getArgument(0);
            scheme.setId(701L);
            return null;
        }).when(persistenceService).create(any(), any());

        var result = service.create(request);

        assertThat(result.getSchemeId()).isEqualTo(701L);
        assertThat(result.getStatus()).isEqualTo("ACTIVE");
        assertThat(result.getItems()).hasSize(2);
        assertThat(result.getItems()).extracting("metricVersion")
                .containsExactly("RUN_SUCCESS_RATE_V1", "TOTAL_DURATION_MS_V1");
        ArgumentCaptor<List<EvaluationWeightSchemeItem>> items = ArgumentCaptor.forClass(List.class);
        verify(persistenceService).create(any(EvaluationWeightScheme.class), items.capture());
        assertThat(items.getValue()).extracting(EvaluationWeightSchemeItem::getWeightPercent)
                .containsExactly(new BigDecimal("60.0000"), new BigDecimal("40.0000"));
    }

    @Test
    void rejectsWeightSumOtherThanExactlyOneHundredPercent() {
        EvaluationWeightSchemeCreateDTO request = request("REVIEW", new BigDecimal("60.0000"),
                new BigDecimal("39.9999"));

        assertInvalid(request);

        verify(persistenceService, never()).create(any(), any());
    }

    @Test
    void rejectsMetricVersionFeatureAndDirectionMismatches() {
        EvaluationWeightSchemeCreateDTO wrongVersion = request("REVIEW", new BigDecimal("60.0000"),
                new BigDecimal("40.0000"));
        wrongVersion.getItems().get(0).setMetricVersion("RUN_SUCCESS_RATE_V2");
        assertInvalid(wrongVersion);

        EvaluationWeightSchemeCreateDTO wrongFeature = request("ASSISTANT", new BigDecimal("60.0000"),
                new BigDecimal("40.0000"));
        wrongFeature.getItems().get(1).setMetricCode("REVIEW_SCORE_STDDEV");
        wrongFeature.getItems().get(1).setMetricVersion("REVIEW_SCORE_STDDEV_V1");
        wrongFeature.getItems().get(1).setUnit("SCORE");
        assertInvalid(wrongFeature);

        EvaluationWeightSchemeCreateDTO wrongDirection = request("REVIEW", new BigDecimal("60.0000"),
                new BigDecimal("40.0000"));
        wrongDirection.getItems().get(0).setNormalizationMethod("LOWER_IS_BETTER");
        assertInvalid(wrongDirection);
    }

    @Test
    void duplicateSchemeVersionMapsToStableBusinessError() {
        EvaluationWeightSchemeCreateDTO request = request("REVIEW", new BigDecimal("60.0000"),
                new BigDecimal("40.0000"));
        doThrow(new DuplicateKeyException("uk_scheme_version"))
                .when(persistenceService).create(any(), any());

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(41112);
    }

    @Test
    void deactivationChangesOnlySchemeStatusAndKeepsItemsReadable() {
        EvaluationWeightScheme active = scheme(701L, "ACTIVE");
        EvaluationWeightScheme inactive = scheme(701L, "INACTIVE");
        inactive.setDeactivatedBy(9L);
        when(schemeMapper.selectById(701L)).thenReturn(active, inactive);
        when(schemeMapper.deactivate(any(), any(), any())).thenReturn(1);
        EvaluationWeightSchemeItem item = new EvaluationWeightSchemeItem();
        item.setSchemeId(701L);
        item.setMetricCode("RUN_SUCCESS_RATE");
        when(itemMapper.selectList(any())).thenReturn(List.of(item));

        var result = service.deactivate(701L, 9L);

        assertThat(result.getStatus()).isEqualTo("INACTIVE");
        assertThat(result.getDeactivatedBy()).isEqualTo(9L);
        assertThat(result.getItems()).hasSize(1);
        verify(schemeMapper).deactivate(any(), any(), any());
        verify(itemMapper, never()).deleteById(any());
    }

    private void assertInvalid(EvaluationWeightSchemeCreateDTO request) {
        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(41111);
    }

    private EvaluationWeightSchemeCreateDTO request(String featureCode,
                                                     BigDecimal successWeight,
                                                     BigDecimal durationWeight) {
        EvaluationWeightSchemeCreateDTO request = new EvaluationWeightSchemeCreateDTO();
        request.setSchemeCode(featureCode + "_BALANCED");
        request.setSchemeVersion(featureCode + "_BALANCED_V1");
        request.setName("均衡方案");
        request.setObjective("兼顾成功率与耗时");
        request.setFeatureCode(featureCode);
        request.setMetricSetVersion(EvaluationMetricRegistry.REGISTRY_VERSION);
        request.setCreatedBy(9L);
        request.setItems(List.of(
                item("RUN_SUCCESS_RATE", "RUN_SUCCESS_RATE_V1", "PERCENT",
                        "HIGHER_IS_BETTER", "0", "100", successWeight),
                item("TOTAL_DURATION_MS", "TOTAL_DURATION_MS_V1", "MILLISECOND",
                        "LOWER_IS_BETTER", "0", "10000", durationWeight)));
        return request;
    }

    private EvaluationWeightItemCreateDTO item(String code, String version, String unit,
                                                String method, String lower, String upper,
                                                BigDecimal weight) {
        EvaluationWeightItemCreateDTO item = new EvaluationWeightItemCreateDTO();
        item.setMetricCode(code);
        item.setMetricVersion(version);
        item.setUnit(unit);
        item.setNormalizationVersion(code + "_NORMALIZATION_V1");
        item.setNormalizationMethod(method);
        item.setClippingPolicy("CLAMP_0_100");
        item.setMissingPolicy("MARK_UNAVAILABLE");
        item.setLowerBound(new BigDecimal(lower));
        item.setUpperBound(new BigDecimal(upper));
        item.setBoundarySource("BUSINESS_THRESHOLD");
        item.setBoundaryReference(code + "_SLO_V1");
        item.setWeightPercent(weight);
        return item;
    }

    private EvaluationWeightScheme scheme(Long id, String status) {
        EvaluationWeightScheme scheme = new EvaluationWeightScheme();
        scheme.setId(id);
        scheme.setSchemeCode("REVIEW_BALANCED");
        scheme.setSchemeVersion("REVIEW_BALANCED_V1");
        scheme.setName("均衡方案");
        scheme.setObjective("兼顾成功率与耗时");
        scheme.setFeatureCode("REVIEW");
        scheme.setMetricSetVersion(EvaluationMetricRegistry.REGISTRY_VERSION);
        scheme.setStatus(status);
        scheme.setCreatedBy(9L);
        return scheme;
    }
}
