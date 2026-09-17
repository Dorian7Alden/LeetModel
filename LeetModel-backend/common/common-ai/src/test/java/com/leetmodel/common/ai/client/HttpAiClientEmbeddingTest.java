package com.leetmodel.common.ai.client;

import com.leetmodel.common.ai.model.AiCallContext;
import com.leetmodel.common.ai.model.AiCallPriority;
import com.leetmodel.common.ai.model.AiEmbeddingRequest;
import com.leetmodel.common.ai.model.AiFeatureCode;
import com.leetmodel.common.ai.model.AiOperationCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class HttpAiClientEmbeddingTest {

    @Test
    void shouldReturnEmbeddingAndUseInternalEndpoint() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://gateway");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://gateway/internal/ai/embeddings"))
                .andRespond(withSuccess(successBody(), MediaType.APPLICATION_JSON));

        var response = new HttpAiClient(builder.build()).embed(request());

        assertThat(response.callId()).isEqualTo("call-1");
        assertThat(response.vectors().get(0).values()).containsExactly(0.1F, 0.2F);
        server.verify();
    }

    @Test
    void shouldMapGatewayErrorAndEmptyData() {
        RestClient.Builder errorBuilder = RestClient.builder().baseUrl("http://gateway");
        MockRestServiceServer errorServer = MockRestServiceServer.bindTo(errorBuilder).build();
        errorServer.expect(requestTo("http://gateway/internal/ai/embeddings"))
                .andRespond(withSuccess("{\"code\":42901,\"message\":\"请求受限\",\"data\":null,\"timestamp\":1}",
                        MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> new HttpAiClient(errorBuilder.build()).embed(request()))
                .isInstanceOfSatisfying(AiClientException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(42901));

        RestClient.Builder emptyBuilder = RestClient.builder().baseUrl("http://gateway");
        MockRestServiceServer emptyServer = MockRestServiceServer.bindTo(emptyBuilder).build();
        emptyServer.expect(requestTo("http://gateway/internal/ai/embeddings"))
                .andRespond(withSuccess("{\"code\":20000,\"message\":\"success\",\"data\":null,\"timestamp\":1}",
                        MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> new HttpAiClient(emptyBuilder.build()).embed(request()))
                .isInstanceOfSatisfying(AiClientException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(50001));
    }

    @Test
    void shouldMapTimeoutWithoutLeakingRequestBody() {
        HttpAiClient client = new HttpAiClient(RestClient.builder().baseUrl("http://gateway")
                .requestFactory((uri, method) -> {
                    throw new IOException("synthetic timeout");
                }).build());

        assertThatThrownBy(() -> client.embed(request()))
                .isInstanceOfSatisfying(AiClientException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo(50002);
                    assertThat(exception.getMessage()).doesNotContain("敏感原文");
                });
    }

    private AiEmbeddingRequest request() {
        AiCallContext context = new AiCallContext("ai-assistant-service", AiFeatureCode.RAG,
                AiOperationCode.RETRIEVE_CONTEXT, "query:1", null, null, "MODEL_CFG_RAG_V1",
                null, AiCallPriority.P0, "query:1", Instant.parse("2099-01-01T00:00:00Z"));
        return AiEmbeddingRequest.single("RAG_V1", context, "敏感原文");
    }

    private String successBody() {
        return """
                {"code":20000,"message":"success","timestamp":1,"data":{
                  "callId":"call-1","logicalModel":"RAG_V1","model":"embedding-3",
                  "dimension":2,"vectors":[{"index":0,"values":[0.1,0.2]}],
                  "usage":null,"cost":null}}
                """;
    }
}
