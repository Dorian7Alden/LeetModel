package com.leetmodel.assistant.workflow;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 仅在显式声明 RUN_NEW_API_SMOKE=true 时执行的真实 New-API gemini-3.8-flash-high 全链路冒烟测试。
 */
@EnabledIfEnvironmentVariable(named = "RUN_NEW_API_SMOKE", matches = "true")
class AssistantEndToEndSmokeTest {

    private final String baseUrl = System.getenv().getOrDefault("NEW_API_BASE_URL", "http://127.0.0.1:3000/v1");
    private final String token = System.getenv().getOrDefault("NEW_API_RELAY_TOKEN", "");

    @Test
    void gemini38EndToEndTextAndFormulaChat() {
        RestClient restClient = RestClient.builder().baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + token)
                .build();
        Map<String, Object> req = Map.of(
                "model", "gemini-3.8-flash-high",
                "messages", List.of(Map.of("role", "user", "content", "请简述最小二乘法的目标函数，并附带LaTeX公式")),
                "max_tokens", 100
        );
        String res = restClient.post().uri("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .body(req)
                .retrieve()
                .body(String.class);

        assertThat(res).isNotBlank();
        assertThat(res).contains("gemini-3.8");
        assertThat(res).contains("$");
    }

    @Test
    void gemini38EndToEndDomainToolPlanning() {
        RestClient restClient = RestClient.builder().baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + token)
                .build();
        Map<String, Object> req = Map.of(
                "model", "gemini-3.8-flash-high",
                "messages", List.of(Map.of("role", "user", "content", "帮我查询一下我所在的队伍现在的状态")),
                "tools", List.of(Map.of(
                        "type", "function",
                        "function", Map.of(
                                "name", "query_user_team",
                                "description", "查询当前用户的组队状态",
                                "parameters", Map.of("type", "object", "properties", Map.of(), "additionalProperties", false)
                        )
                )),
                "tool_choice", "auto"
        );
        String res = restClient.post().uri("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .body(req)
                .retrieve()
                .body(String.class);

        assertThat(res).isNotBlank();
        assertThat(res).contains("query_user_team");
    }
}
