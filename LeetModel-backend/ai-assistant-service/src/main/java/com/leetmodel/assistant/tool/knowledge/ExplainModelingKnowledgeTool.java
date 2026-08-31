package com.leetmodel.assistant.tool.knowledge;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leetmodel.assistant.rag.workflow.RagWorkflowContext;
import com.leetmodel.assistant.rag.workflow.RagWorkflowContextProvider;
import com.leetmodel.assistant.tool.AssistantTool;
import com.leetmodel.assistant.tool.AssistantToolDescriptor;
import com.leetmodel.assistant.tool.AssistantToolException;
import com.leetmodel.assistant.tool.AssistantToolExecutionContext;
import com.leetmodel.assistant.tool.AssistantToolOutput;
import com.leetmodel.common.ai.client.AiClient;
import com.leetmodel.common.ai.model.AiCallContext;
import com.leetmodel.common.ai.model.AiCallPriority;
import com.leetmodel.common.ai.model.AiChatRequest;
import com.leetmodel.common.ai.model.AiChatResponse;
import com.leetmodel.common.ai.model.AiContentPart;
import com.leetmodel.common.ai.model.AiContentType;
import com.leetmodel.common.ai.model.AiFeatureCode;
import com.leetmodel.common.ai.model.AiMessage;
import com.leetmodel.common.ai.model.AiModality;
import com.leetmodel.common.ai.model.AiOperationCode;
import com.leetmodel.common.ai.model.AiResponseFormat;
import com.leetmodel.common.ai.model.AiRole;
import com.leetmodel.common.ai.model.AiToolChoice;
import com.leetmodel.common.ai.model.AiToolChoiceType;
import com.leetmodel.common.ai.model.AiToolDefinition;
import com.leetmodel.common.ai.model.AiToolType;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 使用固定专家 Prompt 和生产 RAG 快照生成最终客服回答的终止型工具。 */
@Component
public class ExplainModelingKnowledgeTool implements AssistantTool<ExplainModelingKnowledgeInput> {

    public static final String PROMPT_VERSION = "PROMPT_ASSISTANT_KNOWLEDGE_0001";
    public static final String MODEL_CONFIG_VERSION = "MODEL_CFG_ASSISTANT_KNOWLEDGE_0001";
    private static final int MAX_ANSWER_CODE_POINTS = 300;
    private static final String OUT_OF_SCOPE = "OUT_OF_SCOPE";
    private static final String OUT_OF_SCOPE_REPLY =
            "我只解答 LeetModel 平台使用和数学建模学习相关问题。";
    private static final AssistantToolDescriptor DESCRIPTOR = new AssistantToolDescriptor(
            "explain_modeling_knowledge", "EXPLAIN_MODELING_KNOWLEDGE_0001",
            new AiToolDefinition(AiToolType.FUNCTION, "explain_modeling_knowledge",
                    "专业、简洁地讲解数学建模概念、方法选择、模型假设、求解步骤和常见误区；"
                            + "不用于平台操作、题目事实、正式论文评审或通用百科。",
                    inputSchema()),
            true, Duration.ofSeconds(120),
            Set.of("ASSISTANT_TOOLS_NO_RAG_V1", "ASSISTANT_TOOLS_RAG_V1"));

    private final AiClient aiClient;
    private final RagWorkflowContextProvider ragContextProvider;
    private final ObjectMapper objectMapper;
    private final String expertPrompt;

    public ExplainModelingKnowledgeTool(AiClient aiClient,
                                        RagWorkflowContextProvider ragContextProvider,
                                        ObjectMapper objectMapper) throws Exception {
        this.aiClient = aiClient;
        this.ragContextProvider = ragContextProvider;
        this.objectMapper = objectMapper;
        this.expertPrompt = new ClassPathResource("prompts/assistant-knowledge-v1.st")
                .getContentAsString(StandardCharsets.UTF_8);
    }

    @Override
    public AssistantToolDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public Class<ExplainModelingKnowledgeInput> inputType() {
        return ExplainModelingKnowledgeInput.class;
    }

    /** 使用禁用工具协议的独立模型调用直接生成最终回答。 */
    @Override
    public AssistantToolOutput execute(ExplainModelingKnowledgeInput input,
                                       AssistantToolExecutionContext context) {
        // RAG 只允许 NONE 或当前回复锁定的物理索引，不读取可变别名
        RagWorkflowContext ragContext = ragContext(input, context);
        List<AiMessage> messages = new ArrayList<>();
        messages.add(message(AiRole.SYSTEM, expertPrompt));
        if (ragContext.present()) messages.add(message(AiRole.SYSTEM, ragContext.text()));
        messages.add(message(AiRole.USER, userQuestion(input)));

        Instant nestedDeadline = earlier(context.deadline(), Instant.now().plusSeconds(120));
        AiCallContext callContext = new AiCallContext(
                "ai-assistant-service", AiFeatureCode.AI_ASSISTANT,
                AiOperationCode.EXPLAIN_MODELING_KNOWLEDGE,
                "assistant-message:" + context.assistantMessageId() + ":tool:" + context.sequenceNo(),
                context.productionSnapshot().workflowVersion(), PROMPT_VERSION,
                MODEL_CONFIG_VERSION, null, ragContext.ragIndexVersion(), AiCallPriority.P0,
                "assistant:" + context.assistantMessageId() + ":attempt:" + context.attemptNo()
                        + ":tool:" + context.sequenceNo(),
                nestedDeadline);
        AiChatResponse response = aiClient.chat(new AiChatRequest(
                AiModality.TEXT, callContext, messages, 500, 0.1,
                AiResponseFormat.TEXT, false, List.of(),
                new AiToolChoice(AiToolChoiceType.NONE, null)));
        if (response != null && response.toolCalls() != null && !response.toolCalls().isEmpty()) {
            throw new AssistantToolException("TOOL_RECURSION_FORBIDDEN", "知识讲解工具不能递归调用工具");
        }
        if (response == null || response.content() == null || response.content().isBlank()) {
            throw new AssistantToolException("KNOWLEDGE_REPLY_EMPTY", "知识讲解未返回回答");
        }

        // 越界标记转换为固定客服范围说明，其他回答按 Unicode 码点强制限制
        boolean outOfScope = OUT_OF_SCOPE.equals(response.content().trim());
        String answer = outOfScope ? OUT_OF_SCOPE_REPLY
                : truncate(clean(response.content()), MAX_ANSWER_CODE_POINTS);
        AiChatResponse terminal = withContent(response, answer);
        String auditSnapshot = auditSnapshot(answer, outOfScope, ragContext);
        return new AssistantToolOutput(auditSnapshot, auditSnapshot, terminal);
    }

    /** 根据生产快照读取固定 RAG 上下文。 */
    private RagWorkflowContext ragContext(ExplainModelingKnowledgeInput input,
                                          AssistantToolExecutionContext context) {
        String ragMode = context.productionSnapshot().ragMode();
        if ("NONE".equals(ragMode) && context.productionSnapshot().ragIndexVersion() == null) {
            return RagWorkflowContext.empty();
        }
        if ("FIXED_INDEX".equals(ragMode)
                && context.productionSnapshot().ragIndexVersion() != null) {
            String query = input.topic() + (input.focus() == null ? "" : " " + input.focus());
            return ragContextProvider.retrieveExact(query,
                    context.productionSnapshot().ragIndexVersion());
        }
        throw new AssistantToolException("KNOWLEDGE_RAG_SNAPSHOT_INVALID",
                "知识讲解 RAG 快照不合法");
    }

    /** 把用户主题包装为明确的不可信数据，模型不能把它当作系统 Prompt。 */
    private String userQuestion(ExplainModelingKnowledgeInput input) {
        KnowledgeLevel level = input.level() == null ? KnowledgeLevel.BEGINNER : input.level();
        return "BEGIN_UNTRUSTED_USER_QUESTION\n"
                + "主题：" + clean(input.topic().trim()) + "\n"
                + "层次：" + level.name() + "\n"
                + "关注点：" + (input.focus() == null ? "无" : clean(input.focus().trim())) + "\n"
                + "END_UNTRUSTED_USER_QUESTION";
    }

    /** 保存知识回答哈希和长度，不在工具表重复保存正文。 */
    private String auditSnapshot(String answer, boolean outOfScope,
                                 RagWorkflowContext ragContext) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("answerSha256", sha256(answer));
        snapshot.put("answerCodePoints", answer.codePointCount(0, answer.length()));
        snapshot.put("outOfScope", outOfScope);
        snapshot.put("ragIndexVersion", ragContext.ragIndexVersion());
        snapshot.put("retrievedChunkCount", ragContext.retrievedChunkCount());
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException exception) {
            throw new AssistantToolException("TOOL_RESULT_INVALID", "知识讲解摘要无法序列化", exception);
        }
    }

    /** 创建替换可见正文、保留调用审计字段的终止响应。 */
    private AiChatResponse withContent(AiChatResponse response, String content) {
        return new AiChatResponse(response.callId(), response.provider(), response.model(),
                response.providerResponseId(), content, response.reasoningContent(),
                response.finishReason(), response.usage(), response.cost(), null);
    }

    /** 构造普通文本消息。 */
    private AiMessage message(AiRole role, String content) {
        return new AiMessage(role,
                List.of(new AiContentPart(AiContentType.TEXT, content, null)));
    }

    /** 返回两个绝对截止时间中更早的一项。 */
    private Instant earlier(Instant first, Instant second) {
        return first.isBefore(second) ? first : second;
    }

    /** 清理所有控制字符，保留普通中文与换行之外的可见文本。 */
    private String clean(String value) {
        StringBuilder builder = new StringBuilder();
        value.codePoints().filter(codePoint -> !Character.isISOControl(codePoint)
                        || codePoint == '\n')
                .forEach(builder::appendCodePoint);
        return builder.toString().trim();
    }

    /** 按 Unicode 码点截断，避免拆开代理字符。 */
    private String truncate(String value, int maxCodePoints) {
        int count = value.codePointCount(0, value.length());
        if (count <= maxCodePoints) return value;
        return value.substring(0, value.offsetByCodePoints(0, maxCodePoints)).trim();
    }

    /** 计算用于历史关联的 SHA-256。 */
    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JVM 缺少 SHA-256", exception);
        }
    }

    /** 构造禁止调用方传入 Prompt、模型和工具字段的输入 Schema。 */
    private static Map<String, Object> inputSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("topic", Map.of("type", "string", "minLength", 2,
                "maxLength", 100, "description", "需要讲解的数学建模学习主题"));
        properties.put("level", Map.of("type", "string",
                "enum", List.of("BEGINNER", "INTERMEDIATE"),
                "description", "讲解层次，默认 BEGINNER"));
        properties.put("focus", Map.of("type", "string", "minLength", 1,
                "maxLength", 100, "description", "可选关注点"));
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", List.of("topic"));
        schema.put("additionalProperties", false);
        return schema;
    }
}
