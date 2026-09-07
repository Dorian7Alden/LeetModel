package com.leetmodel.team.vo;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PopularPracticeProblemVOSerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void serializesProblemIdAsString() throws Exception {
        PopularPracticeProblemVO view = new PopularPracticeProblemVO(
                2092421536012230658L,
                1011,
                "可持续旅游业管理",
                12L
        );

        String json = objectMapper.writeValueAsString(view);

        assertThat(json).contains("\"problemId\":\"2092421536012230658\"");
        assertThat(json).contains("\"practiceCount\":12");
    }
}
