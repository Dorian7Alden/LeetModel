package com.leetmodel.common.core.result;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 统一响应序列化测试。
 */
class ResultSerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("成功判断方法不应序列化为响应字段")
    void successHelperIsNotSerialized() throws Exception {
        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(Result.ok("data")));

        assertTrue(json.has("code"));
        assertTrue(json.has("message"));
        assertTrue(json.has("data"));
        assertTrue(json.has("timestamp"));
        assertFalse(json.has("success"));
    }
}
