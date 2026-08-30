package com.leetmodel.aigateway.provider;

import com.leetmodel.aigateway.config.AiApiProtocol;
import com.leetmodel.common.ai.model.AiChatRequest;
import com.leetmodel.common.ai.model.AiChatResponse;
import com.leetmodel.common.ai.model.AiContentPart;
import com.leetmodel.common.ai.model.AiContentType;
import com.leetmodel.common.ai.model.AiMessage;
import com.leetmodel.common.ai.model.AiMetricCompleteness;
import com.leetmodel.common.ai.model.AiResponseFormat;
import com.leetmodel.common.ai.model.AiRole;
import com.leetmodel.common.ai.model.AiScene;
import com.leetmodel.common.ai.model.AiUsage;
import com.leetmodel.common.ai.model.AiToolCall;
import com.leetmodel.common.ai.model.AiToolChoice;
import com.leetmodel.common.ai.model.AiToolChoiceType;
import com.leetmodel.common.ai.model.AiToolDefinition;
import com.leetmodel.common.ai.model.AiToolType;
import com.leetmodel.common.core.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class NewApiAdapterTest {

    @Test
    void shouldMapTextJsonThinkingAndUsage() {
        Fixture fixture = fixture();
        fixture.server.expect(once(), requestTo("http://new-api.test/v1/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-relay-token"))
                .andExpect(jsonPath("$.model").value("deepseek-v4-flash"))
                .andExpect(jsonPath("$.messages[0].content").value("return json"))
                .andExpect(jsonPath("$.response_format.type").value("json_object"))
                .andExpect(jsonPath("$.thinking.type").value("disabled"))
                .andRespond(withSuccess("""
                        {"id":"relay-response-1","model":"deepseek-v4-flash",
                         "choices":[{"message":{"content":"{\\\"ok\\\":true}","reasoning_content":null},"finish_reason":"stop"}],
                         "usage":{"prompt_tokens":10,"completion_tokens":4,"total_tokens":14,
                           "prompt_cache_hit_tokens":3,"prompt_cache_miss_tokens":7,
                           "completion_tokens_details":{"reasoning_tokens":2}}}
                        """, MediaType.APPLICATION_JSON));

        AiChatResponse response = fixture.adapter.chat(
                "deepseek-v4-flash", AiApiProtocol.OPENAI_COMPLETIONS, textRequest());

        assertThat(response.providerResponseId()).isEqualTo("relay-response-1");
        assertThat(response.content()).isEqualTo("{\"ok\":true}");
        assertThat(response.usage().reasoningTokens()).isEqualTo(2L);
        assertThat(response.usage().complete()).isTrue();
        fixture.server.verify();
    }

    @Test
    void shouldMapMultimodalContentWithoutChannelFields() {
        Fixture fixture = fixture();
        fixture.server.expect(once(), requestTo("http://new-api.test/v1/chat/completions"))
                .andExpect(jsonPath("$.messages[0].content[0].type").value("text"))
                .andExpect(jsonPath("$.messages[0].content[1].type").value("image_url"))
                .andExpect(jsonPath("$.messages[0].content[1].image_url.url")
                        .value("https://fixture.test/page.png"))
                .andRespond(withSuccess("""
                        {"id":"relay-response-2","model":"deepseek-v4-flash-vision-exp",
                         "choices":[{"message":{"content":"ok"},"finish_reason":"stop"}]}
                        """, MediaType.APPLICATION_JSON));

        AiChatResponse response = fixture.adapter.chat("deepseek-v4-flash-vision-exp",
                AiApiProtocol.OPENAI_COMPLETIONS, multimodalRequest());

        assertThat(response.usage().complete()).isFalse();
        assertThat(response.usage().totalTokens()).isNull();
        assertThat(response.usage().completeness()).isEqualTo(AiMetricCompleteness.UNKNOWN);
        fixture.server.verify();
    }

    @Test
    void shouldKeepPartialUsageMissingFieldsUnknown() {
        Fixture fixture = fixture();
        fixture.server.expect(once(), requestTo("http://new-api.test/v1/chat/completions"))
                .andRespond(withSuccess("""
                        {"id":"relay-partial","model":"deepseek-v4-flash",
                         "choices":[{"message":{"content":"ok"},"finish_reason":"stop"}],
                         "usage":{"prompt_tokens":10,"prompt_cache_hit_tokens":3}}
                        """, MediaType.APPLICATION_JSON));

        AiUsage usage = fixture.adapter.chat(
                "deepseek-v4-flash", AiApiProtocol.OPENAI_COMPLETIONS, textRequest()).usage();

        assertThat(usage.completeness()).isEqualTo(AiMetricCompleteness.PARTIAL);
        assertThat(usage.inputTokens()).isEqualTo(10L);
        assertThat(usage.cacheHitTokens()).isEqualTo(3L);
        assertThat(usage.cacheMissTokens()).isNull();
        assertThat(usage.outputTokens()).isNull();
        assertThat(usage.totalTokens()).isNull();
        fixture.server.verify();
    }

    @Test
    void shouldMapAndParseToolCalling() {
        Fixture fixture = fixture();
        fixture.server.expect(once(), requestTo("http://new-api.test/v1/chat/completions"))
                .andExpect(jsonPath("$.tools[0].type").value("function"))
                .andExpect(jsonPath("$.tools[0].function.name").value("search_problem"))
                .andExpect(jsonPath("$.tools[0].function.parameters.type").value("object"))
                .andExpect(jsonPath("$.tools[0].function.parameters.additionalProperties").value(false))
                .andExpect(jsonPath("$.tool_choice").value("auto"))
                .andRespond(withSuccess("""
                        {"id":"relay-tool-1","model":"deepseek-v4-flash",
                         "choices":[{"message":{"content":null,"tool_calls":[
                           {"id":"call-1","type":"function","function":{
                             "name":"search_problem","arguments":"{\\\"code\\\":1001}"}}
                         ]},"finish_reason":"tool_calls"}]}
                        """, MediaType.APPLICATION_JSON));

        AiChatResponse response = fixture.adapter.chat(
                "deepseek-v4-flash", AiApiProtocol.OPENAI_COMPLETIONS, toolRequest());

        assertThat(response.content()).isNull();
        assertThat(response.finishReason()).isEqualTo("tool_calls");
        assertThat(response.toolCalls()).containsExactly(
                new AiToolCall("call-1", "search_problem", "{\"code\":1001}"));
        fixture.server.verify();
    }

    @Test
    void shouldMapAssistantToolCallAndLinkedToolResult() {
        Fixture fixture = fixture();
        fixture.server.expect(once(), requestTo("http://new-api.test/v1/chat/completions"))
                .andExpect(jsonPath("$.messages[1].role").value("assistant"))
                .andExpect(jsonPath("$.messages[1].tool_calls[0].id").value("call-1"))
                .andExpect(jsonPath("$.messages[1].tool_calls[0].function.name").value("search_problem"))
                .andExpect(jsonPath("$.messages[2].role").value("tool"))
                .andExpect(jsonPath("$.messages[2].tool_call_id").value("call-1"))
                .andExpect(jsonPath("$.messages[2].name").value("search_problem"))
                .andRespond(withSuccess("""
                        {"id":"relay-final","model":"deepseek-v4-flash",
                         "choices":[{"message":{"content":"未找到题目"},"finish_reason":"stop"}]}
                        """, MediaType.APPLICATION_JSON));

        AiChatResponse response = fixture.adapter.chat("deepseek-v4-flash",
                AiApiProtocol.OPENAI_COMPLETIONS, toolResultRequest());

        assertThat(response.content()).isEqualTo("未找到题目");
        assertThat(response.toolCalls()).isNull();
        fixture.server.verify();
    }

    @Test
    void shouldRejectToolProtocolForUnverifiedProviderProtocol() {
        Fixture fixture = fixture();

        assertThatThrownBy(() -> fixture.adapter.chat(
                "deepseek-v4-flash", AiApiProtocol.OPENAI_RESPONSES, toolRequest()))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(41202);
        fixture.server.verify();
    }

    @Test
    void shouldRejectResponseWithoutContentOrValidToolCalls() {
        Fixture fixture = fixture();
        fixture.server.expect(once(), requestTo("http://new-api.test/v1/chat/completions"))
                .andRespond(withSuccess("""
                        {"id":"relay-empty","model":"deepseek-v4-flash",
                         "choices":[{"message":{"content":null},"finish_reason":"tool_calls"}]}
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> fixture.adapter.chat(
                "deepseek-v4-flash", AiApiProtocol.OPENAI_COMPLETIONS, toolRequest()))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(51203);
        fixture.server.verify();
    }

    @Test
    void shouldRejectMalformedSuccessResponse() {
        Fixture fixture = fixture();
        fixture.server.expect(once(), requestTo("http://new-api.test/v1/chat/completions"))
                .andRespond(withSuccess("{\"id\":\"bad\",\"choices\":[]}", MediaType.APPLICATION_JSON));

        assertErrorCode(fixture, 51203);
    }

    @Test
    void shouldMapAuthenticationFailure() {
        assertHttpError(HttpStatus.UNAUTHORIZED,
                "{\"error\":{\"type\":\"new_api_error\"}}", 51205);
    }

    @Test
    void shouldMapUnknownModelEvenWhenNewApiUses503() {
        assertHttpError(HttpStatus.SERVICE_UNAVAILABLE,
                "{\"error\":{\"code\":\"model_not_found\"}}", 51208);
    }

    @Test
    void shouldMapQuotaFailure() {
        assertHttpError(HttpStatus.FORBIDDEN,
                "{\"error\":{\"code\":\"insufficient_quota\"}}", 51206);
    }

    @Test
    void shouldMapRateLimit() {
        Fixture fixture = fixture();
        fixture.server.expect(once(), requestTo("http://new-api.test/v1/chat/completions"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                        .header(HttpHeaders.RETRY_AFTER, "7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"type\":\"rate_limit_error\"}}"));
        assertThatThrownBy(() -> fixture.adapter.chat(
                "deepseek-v4-flash", AiApiProtocol.OPENAI_COMPLETIONS, textRequest()))
                .isInstanceOfSatisfying(AiUpstreamRateLimitException.class,
                        exception -> assertThat(exception.getRetryAfter()).isEqualTo(java.time.Duration.ofSeconds(7)));
        fixture.server.verify();
    }

    @Test
    void shouldMapUpstreamFailure() {
        assertHttpError(HttpStatus.BAD_GATEWAY,
                "{\"error\":{\"type\":\"upstream_error\"}}", 51202);
    }

    @Test
    void shouldMapTimeoutWithoutRetrying() {
        Fixture fixture = fixture();
        fixture.server.expect(once(), requestTo("http://new-api.test/v1/chat/completions"))
                .andRespond(withException(new SocketTimeoutException("synthetic timeout")));

        assertErrorCode(fixture, 51204);
        fixture.server.verify();
    }

    private void assertHttpError(HttpStatus status, String body, int expectedCode) {
        Fixture fixture = fixture();
        fixture.server.expect(once(), requestTo("http://new-api.test/v1/chat/completions"))
                .andRespond(withStatus(status).contentType(MediaType.APPLICATION_JSON).body(body));
        assertErrorCode(fixture, expectedCode);
        fixture.server.verify();
    }

    private void assertErrorCode(Fixture fixture, int expectedCode) {
        assertThatThrownBy(() -> fixture.adapter.chat(
                "deepseek-v4-flash", AiApiProtocol.OPENAI_COMPLETIONS, textRequest()))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(expectedCode);
    }

    private Fixture fixture() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://new-api.test/v1");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        return new Fixture(new NewApiAdapter(builder.build(), "test-relay-token"), server);
    }

    private AiChatRequest textRequest() {
        AiMessage message = new AiMessage(AiRole.USER,
                List.of(new AiContentPart(AiContentType.TEXT, "return json", null)));
        return new AiChatRequest(AiScene.GENERAL_TEXT, List.of(message), 128,
                null, AiResponseFormat.JSON_OBJECT, false);
    }

    private AiChatRequest multimodalRequest() {
        AiMessage message = new AiMessage(AiRole.USER, List.of(
                new AiContentPart(AiContentType.TEXT, "review", null),
                new AiContentPart(AiContentType.IMAGE_URL, null, "https://fixture.test/page.png")));
        return new AiChatRequest(AiScene.MULTIMODAL, List.of(message), 128,
                null, null, false);
    }

    private AiChatRequest toolRequest() {
        AiMessage message = new AiMessage(AiRole.USER,
                List.of(new AiContentPart(AiContentType.TEXT, "查询题号 1001", null)));
        return new AiChatRequest(AiScene.GENERAL_TEXT, null, null,
                List.of(message), 128, null, null, false,
                List.of(toolDefinition()), new AiToolChoice(AiToolChoiceType.AUTO, null));
    }

    private AiChatRequest toolResultRequest() {
        AiMessage user = new AiMessage(AiRole.USER,
                List.of(new AiContentPart(AiContentType.TEXT, "查询题号 1001", null)));
        AiMessage assistant = new AiMessage(AiRole.ASSISTANT, List.of(),
                List.of(new AiToolCall("call-1", "search_problem", "{\"code\":1001}")),
                null, null);
        AiMessage tool = new AiMessage(AiRole.TOOL,
                List.of(new AiContentPart(AiContentType.TEXT, "{\"items\":[]}", null)),
                null, "call-1", "search_problem");
        return new AiChatRequest(AiScene.GENERAL_TEXT, null, null,
                List.of(user, assistant, tool), 128, null, null, false,
                List.of(toolDefinition()), new AiToolChoice(AiToolChoiceType.AUTO, null));
    }

    private AiToolDefinition toolDefinition() {
        return new AiToolDefinition(AiToolType.FUNCTION, "search_problem", "查询已发布题目",
                Map.of(
                        "type", "object",
                        "properties", Map.of("code", Map.of("type", "integer")),
                        "required", List.of("code"),
                        "additionalProperties", false
                ));
    }

    private record Fixture(NewApiAdapter adapter, MockRestServiceServer server) {
    }
}
