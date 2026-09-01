package com.leetmodel.common.messaging;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MessagingNamespaceTest {

    @Test
    void shouldCreateEnvironmentScopedResourceNames() {
        MessagingNamespace namespace = new MessagingNamespace("lm-dev");

        assertThat(namespace.topic("review-task-v1")).isEqualTo("lm-dev%review-task-v1");
        assertThat(namespace.consumerGroup("cg-ai-review-task-v1"))
                .isEqualTo("lm-dev%cg-ai-review-task-v1");
    }

    @Test
    void shouldRejectDynamicOrUnsafeResourceNames() {
        MessagingNamespace namespace = new MessagingNamespace("lm-dev");

        assertThatThrownBy(() -> namespace.topic("review/task/*"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
