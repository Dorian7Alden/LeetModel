package com.leetmodel.aigateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

class ModelExecutionCatalogContractTest {

    @Test
    void knowledgeToolUsesDedicatedNoToolsExecutionConfig() throws Exception {
        StandardEnvironment environment = new StandardEnvironment();
        new YamlPropertySourceLoader().load("application",
                        new ClassPathResource("application.yml"))
                .forEach(environment.getPropertySources()::addLast);

        ModelExecutionConfigProperties properties = Binder.get(environment)
                .bind("ai.gateway", ModelExecutionConfigProperties.class)
                .orElseThrow(() -> new IllegalStateException("AI 网关执行配置未绑定"));
        ModelExecutionConfigProperties.Definition definition = properties
                .getExecutionConfigs().get("MODEL_CFG_ASSISTANT_KNOWLEDGE_0001");

        assertThat(definition).isNotNull();
        assertThat(definition.isTools()).isFalse();
        assertThat(definition.getMaxTokens()).isEqualTo(500);
        assertThat(definition.getTemperature()).isEqualTo(0.1);
        assertThat(definition.getPromptVersions())
                .containsExactly("PROMPT_ASSISTANT_KNOWLEDGE_0001");
        assertThat(definition.getWorkflowVersions())
                .containsExactlyInAnyOrder("ASSISTANT_TOOLS_NO_RAG_V1",
                        "ASSISTANT_TOOLS_RAG_V1");
    }
}
