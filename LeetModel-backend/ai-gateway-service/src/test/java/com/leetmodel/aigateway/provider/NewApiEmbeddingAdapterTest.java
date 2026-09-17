package com.leetmodel.aigateway.provider;

import com.leetmodel.aigateway.config.AiEmbeddingProperties;
import com.leetmodel.aigateway.service.AiEmbeddingService;
import com.leetmodel.aigateway.service.AiCallAuditService;
import com.leetmodel.aigateway.service.AiProviderRegistry;
import com.leetmodel.common.ai.model.AiCallContext;
import com.leetmodel.common.ai.model.AiCallPriority;
import com.leetmodel.common.ai.model.AiEmbeddingRequest;
import com.leetmodel.common.ai.model.AiFeatureCode;
import com.leetmodel.common.ai.model.AiMetricCompleteness;
import com.leetmodel.common.ai.model.AiOperationCode;
import com.leetmodel.common.ai.model.AiProvider;
import com.leetmodel.common.core.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class NewApiEmbeddingAdapterTest {

    @Test
    void shouldMapSingleAndCompleteUsage() {
        Fixture fixture = fixture();
        fixture.server.expect(requestTo("http://new-api.test/v1/embeddings"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-relay-token"))
                .andExpect(jsonPath("$.model").value("embedding-model"))
                .andExpect(jsonPath("$.input[0]").value("中文"))
                .andExpect(jsonPath("$.encoding_format").value("float"))
                .andRespond(withSuccess("""
                        {"model":"embedding-model","data":[{"index":0,"embedding":[0.1,-0.2]}],
                         "usage":{"prompt_tokens":3,"total_tokens":3}}
                        """, MediaType.APPLICATION_JSON));

        ProviderEmbeddingResponse response = fixture.adapter.embed("embedding-model", List.of("中文"));

        assertThat(response.vectors()).singleElement().satisfies(vector ->
                assertThat(vector.values()).containsExactly(0.1F, -0.2F));
        assertThat(response.usage().inputTokens()).isEqualTo(3L);
        assertThat(response.usage().outputTokens()).isZero();
        assertThat(response.usage().completeness()).isEqualTo(AiMetricCompleteness.COMPLETE);
        fixture.server.verify();
    }

    @Test
    void shouldPreserveBatchIndexesAndUnknownUsage() {
        Fixture fixture = fixture();
        fixture.server.expect(requestTo("http://new-api.test/v1/embeddings"))
                .andExpect(jsonPath("$.input.length()").value(2))
                .andRespond(withSuccess("""
                        {"model":"embedding-model","data":[
                          {"index":0,"embedding":[0.1,0.2]},
                          {"index":1,"embedding":[0.3,0.4]}]}
                        """, MediaType.APPLICATION_JSON));

        ProviderEmbeddingResponse response = fixture.adapter.embed("embedding-model", List.of("一", "二"));

        assertThat(response.vectors()).extracting("index").containsExactly(0, 1);
        assertThat(response.usage().completeness()).isEqualTo(AiMetricCompleteness.UNKNOWN);
        fixture.server.verify();
    }

    @Test
    void shouldRejectMalformedVector() {
        Fixture fixture = fixture();
        fixture.server.expect(requestTo("http://new-api.test/v1/embeddings"))
                .andRespond(withSuccess("""
                        {"model":"embedding-model","data":[{"index":0,"embedding":[]}]}
                        """, MediaType.APPLICATION_JSON));

        assertCode(() -> fixture.adapter.embed("embedding-model", List.of("x")), 51203);
    }

    @Test
    void shouldRejectDimensionChangeAtGatewayBoundary() {
        Fixture fixture = fixture();
        fixture.server.expect(requestTo("http://new-api.test/v1/embeddings"))
                .andRespond(withSuccess("""
                        {"model":"embedding-model","data":[{"index":0,"embedding":[0.1,0.2]}]}
                        """, MediaType.APPLICATION_JSON));
        AiEmbeddingProperties properties = new AiEmbeddingProperties();
        AiEmbeddingProperties.Binding binding = new AiEmbeddingProperties.Binding();
        binding.setProvider(AiProvider.NEW_API);
        binding.setModel("embedding-model");
        binding.setDimension(3);
        properties.getEmbeddingModels().put("RAG_V1", binding);
        AiEmbeddingService service = new AiEmbeddingService(properties,
                new AiProviderRegistry(List.of(fixture.adapter)), mock(AiCallAuditService.class));
        AiCallContext context = new AiCallContext("ai-assistant-service", AiFeatureCode.RAG,
                AiOperationCode.RETRIEVE_CONTEXT, "query:1", null, null, "MODEL_CFG_RAG_V1",
                null, AiCallPriority.P0, "query:1", Instant.parse("2099-01-01T00:00:00Z"));

        assertCode(() -> service.embed(AiEmbeddingRequest.single("RAG_V1", context, "中文")), 51209);
        fixture.server.verify();
    }

    @Test
    void shouldMapRateLimitAndQuota() {
        Fixture limited = fixture();
        limited.server.expect(requestTo("http://new-api.test/v1/embeddings"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"type\":\"rate_limit_error\"}}"));
        assertCode(() -> limited.adapter.embed("embedding-model", List.of("x")), 51207);

        Fixture quota = fixture();
        quota.server.expect(requestTo("http://new-api.test/v1/embeddings"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"code\":\"insufficient_quota\"}}"));
        assertCode(() -> quota.adapter.embed("embedding-model", List.of("x")), 51206);
    }

    private void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, int code) {
        assertThatThrownBy(call).isInstanceOf(BusinessException.class).extracting("code").isEqualTo(code);
    }

    private Fixture fixture() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://new-api.test/v1");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        return new Fixture(new NewApiAdapter(builder.build(), "test-relay-token"), server);
    }

    private record Fixture(NewApiAdapter adapter, MockRestServiceServer server) {
    }
}
