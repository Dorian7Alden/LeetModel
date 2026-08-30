package com.leetmodel.assistant.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leetmodel.assistant.entity.AssistantMessage;
import com.leetmodel.common.ai.client.HttpAiClient;
import com.leetmodel.common.ai.model.AiChatResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 仅在显式启用时执行的真实 common-ai → AI 网关 → new-api 冒烟。 */
@EnabledIfEnvironmentVariable(named = "RUN_NEW_API_SMOKE", matches = "true")
class AssistantNewApiSmokeTest {

    @Test
    void shouldReplyThroughNewApiWithoutCreatingConversationData() throws Exception {
        String gatewayUrl = System.getenv().getOrDefault("AI_GATEWAY_BASE_URL", "http://127.0.0.1:8090");
        AssistantWorkflow workflow = new AssistantWorkflow(
                new HttpAiClient(RestClient.builder().baseUrl(gatewayUrl).build()),
                new ObjectMapper());
        AssistantMessage message = new AssistantMessage();
        message.setId(1L);
        message.setRole("USER");
        message.setContent("只用一句话回答：什么是线性规划？");

        AiChatResponse response = workflow.reply(List.of(message), message, null,
                new AssistantProductionSnapshot("ASSISTANT_PROD_CFG_0001", 1,
                        "ASSISTANT_NO_RAG_V1", "PROMPT_ASSISTANT_CHAT_0001",
                        "MODEL_CFG_ASSISTANT_TEXT_0001", "NONE", null));

        assertThat(response.callId()).isNotBlank();
        assertThat(response.providerResponseId()).isNotBlank();
        assertThat(response.model()).isEqualTo("deepseek-v4-flash");
        assertThat(response.content()).isNotBlank();
        System.out.printf("assistant-smoke callId=%s providerResponseIdPresent=true model=%s usagePresent=%s%n",
                response.callId(), response.model(), response.usage() != null);
    }
}
