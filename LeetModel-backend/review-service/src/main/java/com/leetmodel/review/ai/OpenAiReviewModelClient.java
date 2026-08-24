package com.leetmodel.review.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(prefix = "review.ai", name = "mode", havingValue = "openai")
public class OpenAiReviewModelClient implements ReviewModelClient {
    private final RestClient client; private final ObjectMapper mapper; private final String model;
    public OpenAiReviewModelClient(@Value("${review.ai.base-url}") String baseUrl,
                                   @Value("${review.ai.api-key}") String apiKey,
                                   @Value("${review.ai.model}") String model, ObjectMapper mapper) {
        this.client = RestClient.builder().baseUrl(baseUrl).defaultHeader("Authorization", "Bearer " + apiKey).build();
        this.mapper = mapper; this.model = model;
    }
    public String review(String paperText) {
        String prompt = "你是数学建模论文评审员。仅返回 JSON，字段为 totalScore、summary、dimensions。总分范围 0 到 100。论文内容：\n"
                + paperText.substring(0, Math.min(paperText.length(), 60000));
        Map<String, Object> body = Map.of("model", model, "temperature", 0.2,
                "response_format", Map.of("type", "json_object"),
                "messages", List.of(Map.of("role", "user", "content", prompt)));
        JsonNode response = client.post().uri("/v1/chat/completions").body(body).retrieve().body(JsonNode.class);
        return response.path("choices").path(0).path("message").path("content").asText();
    }
}
