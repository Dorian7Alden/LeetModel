package com.leetmodel.common.ai.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.List;

/**
 * 统一 AI 对话请求。
 *
 * @param scene 业务场景
 * @param messages 对话消息
 * @param maxTokens 最大输出 Token
 * @param temperature 采样温度
 * @param responseFormat 响应格式
 * @param thinkingEnabled 是否启用思考
 */
public record AiChatRequest(
        AiScene scene,
        AiModality modality,
        @Valid AiCallContext context,
        @NotEmpty List<@Valid AiMessage> messages,
        @Min(1) @Max(131072) Integer maxTokens,
        Double temperature,
        AiResponseFormat responseFormat,
        Boolean thinkingEnabled
) {
    /** 新调用使用的构造器，不再混用旧 scene。 */
    public AiChatRequest(AiModality modality, AiCallContext context, List<AiMessage> messages,
                         Integer maxTokens, Double temperature, AiResponseFormat responseFormat,
                         Boolean thinkingEnabled) {
        this(null, modality, context, messages, maxTokens, temperature, responseFormat, thinkingEnabled);
    }

    /** 旧 Java 调用的源码兼容入口；生产消费者应迁移到 modality + context。 */
    @Deprecated
    public AiChatRequest(AiScene scene, List<AiMessage> messages, Integer maxTokens,
                         Double temperature, AiResponseFormat responseFormat, Boolean thinkingEnabled) {
        this(scene, null, null, messages, maxTokens, temperature, responseFormat, thinkingEnabled);
    }

    @JsonIgnore
    public AiModality effectiveModality() {
        if (modality != null) return modality;
        if (scene == AiScene.MULTIMODAL) return AiModality.MULTIMODAL;
        return scene == AiScene.GENERAL_TEXT ? AiModality.TEXT : null;
    }

    @JsonIgnore
    @AssertTrue(message = "新请求必须同时提供 modality 和 context，旧请求必须提供 scene")
    public boolean isRoutingContextValid() {
        if (modality != null || context != null) return modality != null && context != null;
        return scene != null;
    }

    @JsonIgnore
    @AssertTrue(message = "scene 与 modality 不一致")
    public boolean isLegacySceneCompatible() {
        return scene == null || modality == null || effectiveModality() == modality;
    }
}
