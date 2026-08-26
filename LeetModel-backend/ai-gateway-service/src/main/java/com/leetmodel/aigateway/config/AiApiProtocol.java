package com.leetmodel.aigateway.config;

import com.fasterxml.jackson.annotation.JsonValue;

public enum AiApiProtocol {
    OPENAI_COMPLETIONS("openai-completions"),
    OPENAI_RESPONSES("openai-responses"),
    ANTHROPIC_MESSAGES("anthropic-messages");

    private final String value;
    AiApiProtocol(String value) { this.value = value; }
    @JsonValue public String value() { return value; }
}
