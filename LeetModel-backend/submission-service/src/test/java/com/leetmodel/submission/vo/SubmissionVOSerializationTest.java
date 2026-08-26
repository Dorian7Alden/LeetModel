package com.leetmodel.submission.vo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SubmissionVOSerializationTest {

    @Test
    void shouldSerializeSnowflakeIdsAsStrings() throws Exception {
        SubmissionVO value = SubmissionVO.builder()
                .id(2092424207247020033L)
                .teamId(2092422250541281282L)
                .problemId(2092421536012230658L)
                .submitterId(1001L)
                .build();

        JsonNode json = new ObjectMapper().readTree(new ObjectMapper().writeValueAsString(value));

        assertThat(json.get("id").isTextual()).isTrue();
        assertThat(json.get("teamId").isTextual()).isTrue();
        assertThat(json.get("problemId").isTextual()).isTrue();
        assertThat(json.get("submitterId").isIntegralNumber()).isTrue();
    }
}
