package com.leetmodel.common.api.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AssistantProblemQueryContractTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void acceptsSearchWithExactlyOneKeyAndRecommendationWithoutFilters() {
        AssistantProblemQueryDTO search = new AssistantProblemQueryDTO();
        search.setMode(AssistantProblemQueryMode.SEARCH);
        search.setCode(1001);
        search.setIncludeOverview(true);

        AssistantProblemQueryDTO recommend = new AssistantProblemQueryDTO();
        recommend.setMode(AssistantProblemQueryMode.RECOMMEND);
        recommend.setLimit(3);

        assertThat(validator.validate(search)).isEmpty();
        assertThat(validator.validate(recommend)).isEmpty();
    }

    @Test
    void rejectsAmbiguousSearchAndRecommendationOverview() {
        AssistantProblemQueryDTO ambiguous = new AssistantProblemQueryDTO();
        ambiguous.setMode(AssistantProblemQueryMode.SEARCH);
        ambiguous.setCode(1001);
        ambiguous.setKeyword("优化");

        AssistantProblemQueryDTO recommend = new AssistantProblemQueryDTO();
        recommend.setMode(AssistantProblemQueryMode.RECOMMEND);
        recommend.setIncludeOverview(true);

        assertThat(validator.validate(ambiguous)).isNotEmpty();
        assertThat(validator.validate(recommend)).isNotEmpty();
    }

    @Test
    void validationHelperDoesNotLeakIntoJson() throws Exception {
        AssistantProblemQueryDTO query = new AssistantProblemQueryDTO();
        query.setMode(AssistantProblemQueryMode.SEARCH);
        query.setKeyword("调度");

        String json = objectMapper.writeValueAsString(query);

        assertThat(json).doesNotContain("modeFieldsValid");
        assertThat(objectMapper.readValue(json, AssistantProblemQueryDTO.class).getKeyword())
                .isEqualTo("调度");
    }
}
