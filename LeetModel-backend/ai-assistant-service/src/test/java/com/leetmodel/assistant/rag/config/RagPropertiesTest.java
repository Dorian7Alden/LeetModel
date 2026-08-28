package com.leetmodel.assistant.rag.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class RagPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfiguration.class);

    @Test
    void bindsSafeDefaultsWithRagDisabled() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            RagProperties properties = context.getBean(RagProperties.class);
            assertThat(properties.isEnabled()).isFalse();
            assertThat(properties.getKnowledgeBasePath()).isEqualTo("../rag_kb/数学建模");
            assertThat(properties.getIndexAlias()).isEqualTo("leetmodel-rag-v1-read");
            assertThat(properties.getTopK()).isEqualTo(8);
            assertThat(properties.getScoreThreshold()).isEqualTo(0.65);
            assertThat(properties.getTokenBudget()).isEqualTo(3000);
            assertThat(properties.getEmbeddingBatchSize()).isEqualTo(16);
            assertThat(properties.getRequestTimeout()).isEqualTo(Duration.ofSeconds(5));
            assertThat(properties.getStoreType()).isEqualTo(RagProperties.StoreType.ELASTICSEARCH);
        });
    }

    @Test
    void allowsDeterministicInMemoryStoreForTests() {
        contextRunner.withPropertyValues(
                "assistant.rag.enabled=true",
                "assistant.rag.store-type=IN_MEMORY",
                "assistant.rag.request-timeout=750ms")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    RagProperties properties = context.getBean(RagProperties.class);
                    assertThat(properties.isEnabled()).isTrue();
                    assertThat(properties.getStoreType()).isEqualTo(RagProperties.StoreType.IN_MEMORY);
                    assertThat(properties.getRequestTimeout()).isEqualTo(Duration.ofMillis(750));
                });
    }

    @Test
    void rejectsInvalidNumericAndAliasConfiguration() {
        assertInvalid("assistant.rag.top-k=0");
        assertInvalid("assistant.rag.score-threshold=1.01");
        assertInvalid("assistant.rag.token-budget=127");
        assertInvalid("assistant.rag.embedding-batch-size=129");
        assertInvalid("assistant.rag.index-alias=INVALID Alias");
        assertInvalid("assistant.rag.knowledge-base-path= ");
        assertInvalid("assistant.rag.request-timeout=0s");
    }

    private void assertInvalid(String property) {
        contextRunner.withPropertyValues(property).run(context -> assertThat(context).hasFailed());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(RagProperties.class)
    static class TestConfiguration {
    }
}
