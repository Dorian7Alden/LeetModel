package com.leetmodel.aigateway.service;

import com.leetmodel.common.api.dto.AiCallLogDTO;
import com.leetmodel.common.api.dto.AiCallQueryDTO;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class AiCallQueryContractTest {

    @Test
    void shouldRejectReversedTimeRange() {
        AiCallQueryDTO query = new AiCallQueryDTO();
        query.setCreatedFrom(LocalDateTime.parse("2026-08-29T00:00:00"));
        query.setCreatedTo(LocalDateTime.parse("2026-08-28T00:00:00"));

        var validator = Validation.buildDefaultValidatorFactory().getValidator();

        assertThat(validator.validate(query)).extracting(v -> v.getPropertyPath().toString())
                .contains("timeRangeValid");
    }

    @Test
    void queryAndStatsMustShareBusinessFiltersAndCurrencyGuard() throws Exception {
        String xml;
        try (var input = getClass().getResourceAsStream("/mapper/AiCallLogMapper.xml")) {
            assertThat(input).isNotNull();
            xml = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertThat(xml).contains("evaluation_task_id = TRIM(#{evaluationTaskId})")
                .contains("business_task_id = TRIM(#{businessTaskId})")
                .contains("model_execution_config_version = TRIM(#{modelExecutionConfigVersion})")
                .contains("COUNT(DISTINCT CASE WHEN cost_amount IS NOT NULL THEN cost_currency END)")
                .contains("actual_cost_count", "estimated_cost_count", "unknown_cost_count");
    }

    @Test
    void publicCallDtoMustNotExposeBodiesOrSecrets() {
        assertThat(AiCallLogDTO.class.getDeclaredFields())
                .extracting(java.lang.reflect.Field::getName)
                .doesNotContain("prompt", "content", "responseBody", "reasoningContent",
                        "paper", "knowledge", "relayToken", "apiKey");
    }
}
