package com.leetmodel.aigateway.provider;

import com.leetmodel.aigateway.config.DeepSeekProperties;
import com.leetmodel.common.ai.model.AiChatRequest;
import com.leetmodel.common.ai.model.AiChatResponse;
import com.leetmodel.common.ai.model.AiContentPart;
import com.leetmodel.common.ai.model.AiContentType;
import com.leetmodel.common.ai.model.AiMessage;
import com.leetmodel.common.ai.model.AiRole;
import com.leetmodel.common.ai.model.AiScene;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class DeepSeekAdapterTest {

    @Test
    void shouldNormalizeDeepSeekUsage() {
        RestClient.Builder builder = RestClient.builder();
        builder.baseUrl("https://api.deepseek.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        DeepSeekAdapter adapter = new DeepSeekAdapter(builder.build(), "test-key");

        server.expect(once(), requestTo("https://api.deepseek.test/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {
                          "id":"ds-1",
                          "model":"deepseek-v4-pro",
                          "choices":[{
                            "message":{"content":"ok","reasoning_content":"think"},
                            "finish_reason":"stop"
                          }],
                          "usage":{
                            "prompt_tokens":100,
                            "prompt_cache_hit_tokens":60,
                            "prompt_cache_miss_tokens":40,
                            "completion_tokens":20,
                            "total_tokens":120,
                            "completion_tokens_details":{"reasoning_tokens":12}
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        AiChatResponse response = adapter.chat("deepseek-v4-pro", textRequest());

        assertThat(response.providerResponseId()).isEqualTo("ds-1");
        assertThat(response.usage().cacheHitTokens()).isEqualTo(60L);
        assertThat(response.usage().cacheMissTokens()).isEqualTo(40L);
        assertThat(response.usage().reasoningTokens()).isEqualTo(12L);
        server.verify();
    }

    @Test
    void shouldSendVisionImageUsingDeepSeekMultimodalProtocol() {
        RestClient.Builder builder = RestClient.builder();
        builder.baseUrl("https://api.deepseek.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        DeepSeekAdapter adapter = new DeepSeekAdapter(builder.build(), "test-key");
        server.expect(once(), requestTo("https://api.deepseek.test/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.model").value("deepseek-v4-flash-vision-exp"))
                .andExpect(jsonPath("$.messages[0].content[0].type").value("text"))
                .andExpect(jsonPath("$.messages[0].content[1].type").value("image_url"))
                .andExpect(jsonPath("$.messages[0].content[1].image_url.url").value("data:image/jpeg;base64,AA=="))
                .andRespond(withSuccess("""
                        {"id":"ds-v1","model":"deepseek-v4-flash-vision-exp",
                         "choices":[{"message":{"content":"{\\\"score\\\":80}"},"finish_reason":"stop"}],
                         "usage":{"prompt_tokens":10,"completion_tokens":5,"total_tokens":15}}
                        """, MediaType.APPLICATION_JSON));
        AiMessage message = new AiMessage(AiRole.USER, List.of(
                new AiContentPart(AiContentType.TEXT, "review", null),
                new AiContentPart(AiContentType.IMAGE_URL, null, "data:image/jpeg;base64,AA==")));
        AiChatRequest request = new AiChatRequest(AiScene.MULTIMODAL, List.of(message), 100, 0.1, null, false);
        assertThat(adapter.chat("deepseek-v4-flash-vision-exp", request).model())
                .isEqualTo("deepseek-v4-flash-vision-exp");
        server.verify();
    }

    private AiChatRequest textRequest() {
        AiContentPart content = new AiContentPart(AiContentType.TEXT, "hello", null);
        AiMessage message = new AiMessage(AiRole.USER, List.of(content));
        return new AiChatRequest(AiScene.GENERAL_TEXT, List.of(message), 100, null, null, false);
    }
}
