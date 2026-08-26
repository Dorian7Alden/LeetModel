package com.leetmodel.aigateway.provider;

import com.leetmodel.aigateway.config.AiApiProtocol;
import com.leetmodel.common.ai.model.AiChatRequest;
import com.leetmodel.common.ai.model.AiChatResponse;
import com.leetmodel.common.ai.model.AiContentPart;
import com.leetmodel.common.ai.model.AiContentType;
import com.leetmodel.common.ai.model.AiMessage;
import com.leetmodel.common.ai.model.AiResponseFormat;
import com.leetmodel.common.ai.model.AiRole;
import com.leetmodel.common.ai.model.AiScene;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AiProtocolAdapterTest {

    @Test
    void shouldExecuteOpenAiResponsesProtocol() {
        Fixture fixture = fixture();
        fixture.server.expect(once(), requestTo("https://gateway.test/responses"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-key"))
                .andExpect(jsonPath("$.input[0].content[0].type").value("input_text"))
                .andExpect(jsonPath("$.input[0].content[1].type").value("input_image"))
                .andExpect(jsonPath("$.max_output_tokens").value(512))
                .andExpect(jsonPath("$.text.format.type").value("json_schema"))
                .andRespond(withSuccess("""
                        {"id":"resp-1","model":"response-model","status":"completed",
                         "output":[{"type":"message","content":[{"type":"output_text","text":"{\\\"score\\\":91}"}]}],
                         "usage":{"input_tokens":100,"output_tokens":20,"total_tokens":120,
                           "input_tokens_details":{"cached_tokens":30},
                           "output_tokens_details":{"reasoning_tokens":5}}}
                        """, MediaType.APPLICATION_JSON));

        AiChatResponse response = fixture.adapter.chat(
                "response-model", AiApiProtocol.OPENAI_RESPONSES, multimodalRequest());

        assertThat(response.content()).isEqualTo("{\"score\":91}");
        assertThat(response.finishReason()).isEqualTo("completed");
        assertThat(response.usage().cacheHitTokens()).isEqualTo(30L);
        assertThat(response.usage().cacheMissTokens()).isEqualTo(70L);
        assertThat(response.usage().reasoningTokens()).isEqualTo(5L);
        fixture.server.verify();
    }

    @Test
    void shouldExecuteAnthropicMessagesProtocol() {
        Fixture fixture = fixture();
        fixture.server.expect(once(), requestTo("https://gateway.test/messages"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-api-key", "test-key"))
                .andExpect(header("anthropic-version", "2023-06-01"))
                .andExpect(jsonPath("$.system").value("Return JSON"))
                .andExpect(jsonPath("$.messages[0].content[0].type").value("text"))
                .andExpect(jsonPath("$.messages[0].content[1].source.type").value("base64"))
                .andExpect(jsonPath("$.messages[0].content[1].source.media_type").value("image/jpeg"))
                .andExpect(jsonPath("$.messages[0].content[1].source.data").value("AA=="))
                .andExpect(jsonPath("$.output_config.format.type").value("json_schema"))
                .andRespond(withSuccess("""
                        {"id":"msg-1","model":"claude-test","stop_reason":"end_turn",
                         "content":[{"type":"thinking","thinking":"reason"},{"type":"text","text":"{\\\"score\\\":87}"}],
                         "usage":{"input_tokens":80,"output_tokens":20,"cache_read_input_tokens":25,
                           "cache_creation_input_tokens":10,"output_tokens_details":{"thinking_tokens":6}}}
                        """, MediaType.APPLICATION_JSON));

        AiChatResponse response = fixture.adapter.chat(
                "claude-test", AiApiProtocol.ANTHROPIC_MESSAGES, anthropicRequest());

        assertThat(response.content()).isEqualTo("{\"score\":87}");
        assertThat(response.reasoningContent()).isEqualTo("reason");
        assertThat(response.finishReason()).isEqualTo("end_turn");
        assertThat(response.usage().totalTokens()).isEqualTo(100L);
        assertThat(response.usage().reasoningTokens()).isEqualTo(6L);
        fixture.server.verify();
    }

    private Fixture fixture() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://gateway.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        return new Fixture(new DeepSeekAdapter(builder.build(), "test-key"), server);
    }

    private AiChatRequest multimodalRequest() {
        AiMessage user = new AiMessage(AiRole.USER, List.of(
                new AiContentPart(AiContentType.TEXT, "review", null),
                new AiContentPart(AiContentType.IMAGE_URL, null, "data:image/jpeg;base64,AA==")
        ));
        return new AiChatRequest(AiScene.MULTIMODAL, List.of(user), 512, 0.1,
                AiResponseFormat.JSON_OBJECT, false);
    }

    private AiChatRequest anthropicRequest() {
        AiMessage system = new AiMessage(AiRole.SYSTEM,
                List.of(new AiContentPart(AiContentType.TEXT, "Return JSON", null)));
        AiMessage user = new AiMessage(AiRole.USER, List.of(
                new AiContentPart(AiContentType.TEXT, "review", null),
                new AiContentPart(AiContentType.IMAGE_URL, null, "data:image/jpeg;base64,AA==")
        ));
        return new AiChatRequest(AiScene.MULTIMODAL, List.of(system, user), 512, null,
                AiResponseFormat.JSON_OBJECT, null);
    }

    private record Fixture(DeepSeekAdapter adapter, MockRestServiceServer server) {}
}
