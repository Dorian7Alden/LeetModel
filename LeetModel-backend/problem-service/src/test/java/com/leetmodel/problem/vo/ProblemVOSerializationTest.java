package com.leetmodel.problem.vo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProblemVOSerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void serializesProblemIdAsStringWithoutPrecisionLoss() throws Exception {
        ProblemVO problem = ProblemVO.builder()
                .id(2091483544439365634L)
                .title("测试题目")
                .build();

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(problem));

        assertTrue(json.get("id").isTextual());
        assertEquals("2091483544439365634", json.get("id").asText());
    }
}
