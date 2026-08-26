package com.leetmodel.review.vo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReviewVOSerializationTest {

    @Test
    void shouldSerializeSnowflakeIdsAsStrings() throws Exception {
        ReviewVO value = ReviewVO.builder()
                .taskId(2092424208283013121L)
                .submissionId(2092424207247020033L)
                .build();

        JsonNode json = new ObjectMapper().readTree(new ObjectMapper().writeValueAsString(value));

        assertThat(json.get("taskId").isTextual()).isTrue();
        assertThat(json.get("submissionId").isTextual()).isTrue();
    }
}
