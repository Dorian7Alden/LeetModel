package com.leetmodel.aigateway.provider;

import com.leetmodel.aigateway.config.AiApiProtocol;
import com.leetmodel.common.ai.model.AiChatRequest;
import com.leetmodel.common.ai.model.AiChatResponse;
import com.leetmodel.common.ai.model.AiContentPart;
import com.leetmodel.common.ai.model.AiContentType;
import com.leetmodel.common.ai.model.AiMessage;
import com.leetmodel.common.ai.model.AiRole;
import com.leetmodel.common.ai.model.AiScene;
import com.leetmodel.common.ai.model.AiToolChoice;
import com.leetmodel.common.ai.model.AiToolChoiceType;
import com.leetmodel.common.ai.model.AiToolDefinition;
import com.leetmodel.common.ai.model.AiToolType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 仅在本地提供 NEW_API_RELAY_TOKEN 时执行的真实 Gemini 3.8 Flash High 连通性冒烟测试。
 */
@EnabledIfEnvironmentVariable(named = "RUN_NEW_API_SMOKE", matches = "true")
class GeminiNewApiSmokeTest {

    @Test
    void shouldChatWithGemini38FlashHigh() {
        NewApiAdapter adapter = createAdapter();
        AiMessage message = new AiMessage(AiRole.USER,
                List.of(new AiContentPart(AiContentType.TEXT, "请用一句话回答：什么是二分查找？", null)));
        AiChatRequest request = new AiChatRequest(AiScene.GENERAL_TEXT, List.of(message), 64,
                null, null, false);

        AiChatResponse response = adapter.chat("gemini-3.8-flash-high", AiApiProtocol.OPENAI_COMPLETIONS, request);

        assertThat(response).isNotNull();
        assertThat(response.content()).isNotBlank();
        assertThat(response.model()).startsWith("gemini-3.8");
        assertThat(response.finishReason()).isEqualTo("stop");
    }

    @Test
    void shouldSupportToolCallingWithGemini38FlashHigh() {
        NewApiAdapter adapter = createAdapter();
        AiMessage message = new AiMessage(AiRole.USER,
                List.of(new AiContentPart(AiContentType.TEXT, "请帮我查询题号 1001 的题目详情", null)));
        AiToolDefinition tool = new AiToolDefinition(
                AiToolType.FUNCTION,
                "search_problem",
                "根据题号查询题目详情",
                Map.of(
                        "type", "object",
                        "properties", Map.of("code", Map.of("type", "integer", "description", "题号")),
                        "required", List.of("code"),
                        "additionalProperties", false
                )
        );
        AiChatRequest request = new AiChatRequest(AiScene.GENERAL_TEXT, null, null,
                List.of(message), 128, null, null, false,
                List.of(tool), new AiToolChoice(AiToolChoiceType.AUTO, null));

        AiChatResponse response = adapter.chat("gemini-3.8-flash-high", AiApiProtocol.OPENAI_COMPLETIONS, request);

        assertThat(response).isNotNull();
        assertThat(response.finishReason()).isEqualTo("tool_calls");
        assertThat(response.toolCalls()).isNotEmpty();
        assertThat(response.toolCalls().get(0).name()).isEqualTo("search_problem");
        assertThat(response.toolCalls().get(0).argumentsJson()).contains("1001");
    }

    private NewApiAdapter createAdapter() {
        String baseUrl = System.getenv().getOrDefault("NEW_API_BASE_URL", "http://127.0.0.1:3000/v1");
        String token = System.getenv().getOrDefault("NEW_API_RELAY_TOKEN", "");
        RestClient restClient = RestClient.builder().baseUrl(baseUrl).build();
        return new NewApiAdapter(restClient, token);
    }
}
