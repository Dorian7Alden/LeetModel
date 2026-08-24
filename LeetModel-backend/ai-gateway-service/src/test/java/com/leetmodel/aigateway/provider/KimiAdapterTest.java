package com.leetmodel.aigateway.provider;

import com.leetmodel.aigateway.config.KimiProperties;
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
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class KimiAdapterTest {

    @Test
    void shouldNormalizeKimiUsage() {
        RestClient.Builder builder = RestClient.builder();
        builder.baseUrl("https://api.kimi.test/v1");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KimiAdapter adapter = new KimiAdapter(builder.build(), "test-key");

        server.expect(once(), requestTo("https://api.kimi.test/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {
                          "id":"kimi-1",
                          "model":"kimi-k2.6",
                          "choices":[{
                            "message":{"content":"ok","reasoning_content":"think"},
                            "finish_reason":"stop"
                          }],
                          "usage":{
                            "prompt_tokens":100,
                            "cached_tokens":70,
                            "completion_tokens":20,
                            "total_tokens":120
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        AiChatResponse response = adapter.chat("kimi-k2.6", textRequest());

        assertThat(response.providerResponseId()).isEqualTo("kimi-1");
        assertThat(response.usage().cacheHitTokens()).isEqualTo(70L);
        assertThat(response.usage().cacheMissTokens()).isEqualTo(30L);
        assertThat(response.usage().reasoningTokens()).isNull();
        server.verify();
    }

    private AiChatRequest textRequest() {
        AiContentPart content = new AiContentPart(AiContentType.TEXT, "hello", null);
        AiMessage message = new AiMessage(AiRole.USER, List.of(content));
        return new AiChatRequest(AiScene.GENERAL_TEXT, List.of(message), 100, null, null, false);
    }
}
