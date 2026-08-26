package com.leetmodel.aigateway.service;

import com.leetmodel.aigateway.config.AiApiProtocol;
import com.leetmodel.aigateway.config.AiModelCatalogProperties;
import com.leetmodel.aigateway.config.AiRoutingProperties;
import com.leetmodel.aigateway.provider.AiProviderAdapter;
import com.leetmodel.common.ai.model.AiChatRequest;
import com.leetmodel.common.ai.model.AiChatResponse;
import com.leetmodel.common.ai.model.AiContentPart;
import com.leetmodel.common.ai.model.AiContentType;
import com.leetmodel.common.ai.model.AiMessage;
import com.leetmodel.common.ai.model.AiProvider;
import com.leetmodel.common.ai.model.AiRole;
import com.leetmodel.common.ai.model.AiScene;
import com.leetmodel.common.ai.model.AiUsage;
import com.leetmodel.common.core.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiChatServiceTest {

    @Mock private AiProviderRegistry registry;
    @Mock private AiProviderAdapter adapter;
    @Mock private AiCallAuditService auditService;
    private AiRoutingProperties routes;
    private AiModelCatalogProperties models;

    @BeforeEach
    void setUp() {
        routes = new AiRoutingProperties();
        models = new AiModelCatalogProperties();
    }

    @Test
    void shouldAssignCallIdAndAuditSuccessfulCall() {
        configureRoute();
        when(registry.get(AiProvider.DEEPSEEK)).thenReturn(adapter);
        AiChatResponse providerResponse = new AiChatResponse(null, AiProvider.DEEPSEEK,
                "deepseek-test", "provider-id", "answer", null, "stop",
                new AiUsage(2L, 0L, 2L, 3L, 0L, 5L, true));
        when(adapter.chat(eq("deepseek-test"), eq(AiApiProtocol.OPENAI_COMPLETIONS), any()))
                .thenReturn(providerResponse);

        AiChatResponse response = service().chat(request());

        assertThat(response.callId()).isNotBlank();
        verify(auditService).recordSuccess(eq(response.callId()), any(), eq("DEEPSEEK"),
                eq("deepseek-test"), eq(providerResponse), anyLong());
    }

    @Test
    void shouldAuditRouteFailureWithoutLeakingRequestContent() {
        AiChatRequest request = request();

        assertThatThrownBy(() -> service().chat(request))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(41101);
        verify(auditService).recordFailure(any(), eq(request), eq(null), eq(null),
                any(BusinessException.class), anyLong());
    }

    private AiChatService service() {
        return new AiChatService(routes, registry, models, auditService);
    }

    private void configureRoute() {
        AiRoutingProperties.Route route = new AiRoutingProperties.Route();
        route.setProvider(AiProvider.DEEPSEEK);
        route.setModel("deepseek-test");
        routes.setRoutes(Map.of(AiScene.GENERAL_TEXT, route));

        AiModelCatalogProperties.ModelProfile profile = new AiModelCatalogProperties.ModelProfile();
        profile.setProtocol(AiApiProtocol.OPENAI_COMPLETIONS);
        profile.setInputTypes(Set.of(AiContentType.TEXT));
        profile.setMaxOutputTokens(100);
        profile.setContextTokens(1000);
        models.setModels(Map.of("DEEPSEEK/deepseek-test", profile));
    }

    private AiChatRequest request() {
        AiMessage message = new AiMessage(AiRole.USER,
                List.of(new AiContentPart(AiContentType.TEXT, "private prompt", null)));
        return new AiChatRequest(AiScene.GENERAL_TEXT, List.of(message), 10, null, null, false);
    }
}
