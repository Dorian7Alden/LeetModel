package com.leetmodel.assistant.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leetmodel.assistant.rag.workflow.RagWorkflowContext;
import com.leetmodel.assistant.rag.workflow.RagWorkflowContextProvider;
import com.leetmodel.assistant.workflow.AssistantWorkflow;
import com.leetmodel.common.ai.model.AiChatResponse;
import com.leetmodel.common.api.dto.*;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** 无正式会话副作用的客服版本目录与单轮隔离实验。 */
@Service
public class AssistantExperimentService {
    public static final String NO_RAG_VERSION = "ASSISTANT_NO_RAG_V1";
    public static final String RAG_VERSION = "ASSISTANT_RAG_V1";
    public static final String MODEL_CONFIG = "MODEL_CFG_ASSISTANT_TEXT_0001";

    private final AssistantWorkflow workflow;
    private final RagWorkflowContextProvider ragContextProvider;
    private final ObjectMapper objectMapper;

    public AssistantExperimentService(AssistantWorkflow workflow,
                                      RagWorkflowContextProvider ragContextProvider,
                                      ObjectMapper objectMapper) {
        this.workflow = workflow;
        this.ragContextProvider = ragContextProvider;
        this.objectMapper = objectMapper;
    }

    public AiFeatureDefinitionDTO featureDefinition() {
        return new AiFeatureDefinitionDTO("ASSISTANT", "AI 客服", "ai-assistant-service",
                List.of("QUESTION"), List.of("RUN_SUCCESS", "DURATION_MS"), List.of(
                new AiWorkflowVersionDTO(NO_RAG_VERSION, "客服单轮无 RAG V1", "ENABLED",
                        "ASSISTANT_QUESTION_V1", "ASSISTANT_REPLY_V1",
                        "固定系统 Prompt 与模型配置，不读取或写入正式会话"),
                new AiWorkflowVersionDTO(RAG_VERSION, "客服单轮 RAG V1", "ENABLED",
                        "ASSISTANT_QUESTION_V1", "ASSISTANT_REPLY_V1",
                        "必须锁定已构建的 ragIndexVersion，不允许检索失败后降级")));
    }

    public AiExperimentResultDTO run(AiExperimentRequestDTO request) {
        Instant startedAt = Instant.now();
        try {
            validate(request);
            String question = objectMapper.readTree(request.getSample().getPayloadJson())
                    .required("question").asText();
            if (question.isBlank() || question.length() > 8000) {
                throw new IllegalArgumentException("客服实验问题长度不合法");
            }
            boolean withRag = RAG_VERSION.equals(request.getWorkflowVersion());
            RagWorkflowContext rag = withRag
                    ? ragContextProvider.retrieveExact(question, request.getRagIndexVersion())
                    : RagWorkflowContext.empty();
            AiChatResponse response = workflow.experimentReply(question, rag,
                    request.getExperimentRunId(), request.getWorkflowVersion(),
                    request.getModelExecutionConfigVersion());
            String output = objectMapper.writeValueAsString(Map.of("answer", response.content()));
            return result(request, "SUCCEEDED", null, output, response.model(), response.callId(),
                    Duration.between(startedAt, Instant.now()).toMillis(), null);
        } catch (Exception exception) {
            String failureType = exception instanceof IllegalArgumentException
                    ? "CONFIGURATION" : "DEPENDENCY";
            return result(request, "FAILED", failureType, null, null, null,
                    Duration.between(startedAt, Instant.now()).toMillis(),
                    "客服实验未能按锁定配置完成");
        }
    }

    private void validate(AiExperimentRequestDTO request) {
        boolean rag = RAG_VERSION.equals(request.getWorkflowVersion());
        boolean noRag = NO_RAG_VERSION.equals(request.getWorkflowVersion());
        boolean matches = "ASSISTANT".equals(request.getFeatureCode())
                && "QUESTION".equals(request.getSample().getSampleType())
                && "ASSISTANT_QUESTION_V1".equals(request.getSample().getSchemaVersion())
                && MODEL_CONFIG.equals(request.getModelExecutionConfigVersion())
                && ((rag && request.getRagIndexVersion() != null
                && !request.getRagIndexVersion().isBlank())
                || (noRag && request.getRagIndexVersion() == null));
        if (!matches) throw new IllegalArgumentException("客服实验配置与版本不匹配");
    }

    private AiExperimentResultDTO result(AiExperimentRequestDTO request, String status,
                                         String failureType, String output, String model,
                                         String callId, Long duration, String error) {
        return new AiExperimentResultDTO(request.getExperimentRunId(), "ASSISTANT",
                request.getWorkflowVersion(), request.getModelExecutionConfigVersion(),
                request.getRagIndexVersion(), status, failureType, "ASSISTANT_REPLY_V1", output,
                "ASSISTANT_RUN_METRICS_V1", "SUCCEEDED".equals(status) ? "{}" : null,
                model, callId, duration, error);
    }
}
