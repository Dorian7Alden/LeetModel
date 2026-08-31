package com.leetmodel.aigateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

class ModelExecutionCatalogContractTest {

    @Test
    void outerAssistantToolWorkflowUsesToolsEnabledExecutionConfig() throws Exception {
        ModelExecutionConfigProperties properties = properties();
        ModelExecutionConfigProperties.Definition definition = properties
                .getExecutionConfigs().get("MODEL_CFG_ASSISTANT_TOOLS_0001");

        assertThat(definition).isNotNull();
        assertThat(definition.isTools()).isTrue();
        assertThat(definition.getPromptVersions())
                .containsExactly("PROMPT_ASSISTANT_TOOLS_0001");
        assertThat(definition.getWorkflowVersions())
                .containsExactlyInAnyOrder("ASSISTANT_TOOLS_NO_RAG_V1",
                        "ASSISTANT_TOOLS_RAG_V1");
    }

    @Test
    void knowledgeToolUsesDedicatedNoToolsExecutionConfig() throws Exception {
        ModelExecutionConfigProperties properties = properties();
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

    @Test
    void groundedPaperWorkflowsHaveDedicatedImmutableTextConfigs() throws Exception {
        ModelExecutionConfigProperties properties = properties();

        assertTextConfig(properties, "MODEL_CFG_REVIEW_TEXT_0002", 8192, 0.1,
                "PROMPT_EVIDENCE_REVIEW_0001", "EVIDENCE_REVIEW_V2");
        assertTextConfig(properties, "MODEL_CFG_SUGGESTION_TEXT_0002", 8192, 0.15,
                "PROMPT_GROUNDED_SUGGESTION_0001", "GROUNDED_SUGGESTION_V2");
        assertTextConfig(properties, "MODEL_CFG_KNOWLEDGE_DIRECTORY_0001", 1200, 0.0,
                "PROMPT_AI_DIRECTORY_0001", "AI_DIRECTORY_V1");
    }

    private void assertTextConfig(ModelExecutionConfigProperties properties, String version,
                                  int maxTokens, double temperature, String prompt, String workflow) {
        ModelExecutionConfigProperties.Definition definition = properties.getExecutionConfigs().get(version);
        assertThat(definition).isNotNull();
        assertThat(definition.getCallType()).isEqualTo("CHAT");
        assertThat(definition.getMaxTokens()).isEqualTo(maxTokens);
        assertThat(definition.getTemperature()).isEqualTo(temperature);
        assertThat(definition.getPromptVersions()).containsExactly(prompt);
        assertThat(definition.getWorkflowVersions()).containsExactly(workflow);
    }

    private ModelExecutionConfigProperties properties() throws Exception {
        StandardEnvironment environment = new StandardEnvironment();
        new YamlPropertySourceLoader().load("application",
                        new ClassPathResource("application.yml"))
                .forEach(environment.getPropertySources()::addLast);
        return Binder.get(environment)
                .bind("ai.gateway", ModelExecutionConfigProperties.class)
                .orElseThrow(() -> new IllegalStateException("AI 网关执行配置未绑定"));
    }
}
