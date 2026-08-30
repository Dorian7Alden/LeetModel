package com.leetmodel.assistant.tool;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leetmodel.assistant.tool.problem.RecommendProblemTool;
import com.leetmodel.assistant.tool.problem.SearchProblemTool;
import com.leetmodel.common.ai.model.AiToolDefinition;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * AI 客服不可变工具集注册表。
 *
 * <p>工具只通过显式构造参数注册，不根据模型文本、类名或 Bean 名动态解析。</p>
 */
@Component
public class AssistantToolRegistry {

    public static final String TOOLSET_V1 = "ASSISTANT_TOOLSET_0001";
    private static final int MAX_ARGUMENT_JSON_LENGTH = 4000;

    private final ObjectMapper objectMapper;
    private final Validator validator;
    private final Map<String, Map<String, AssistantTool<?>>> toolsets;

    public AssistantToolRegistry(ObjectMapper objectMapper, Validator validator,
                                 SearchProblemTool searchProblemTool,
                                 RecommendProblemTool recommendProblemTool) {
        this.objectMapper = objectMapper;
        this.validator = validator;
        Map<String, AssistantTool<?>> v1 = new LinkedHashMap<>();
        register(v1, searchProblemTool);
        register(v1, recommendProblemTool);
        this.toolsets = Map.of(TOOLSET_V1, Map.copyOf(v1));
    }

    /**
     * 返回当前工具集和工作流可以暴露的固定工具定义。
     *
     * @param toolsetVersion 工具集版本
     * @param workflowVersion 工作流版本
     * @return 工具定义
     */
    public List<AiToolDefinition> definitions(String toolsetVersion, String workflowVersion) {
        Map<String, AssistantTool<?>> tools = requiredToolset(toolsetVersion);
        List<AiToolDefinition> definitions = tools.values().stream()
                .filter(tool -> tool.descriptor().allowedWorkflowVersions().contains(workflowVersion))
                .map(tool -> tool.descriptor().definition()).toList();
        if (definitions.isEmpty()) {
            throw new AssistantToolException("TOOLSET_WORKFLOW_MISMATCH", "当前工作流不允许使用该工具集");
        }
        return definitions;
    }

    /**
     * 查找工具描述，供收到调用后先写审计事实。
     *
     * @param toolsetVersion 工具集版本
     * @param name 模型返回工具名
     * @return 已注册工具
     */
    public Optional<AssistantTool<?>> find(String toolsetVersion, String name) {
        Map<String, AssistantTool<?>> tools = toolsets.get(toolsetVersion);
        return tools == null ? Optional.empty() : Optional.ofNullable(tools.get(name));
    }

    /**
     * 校验工具白名单、工作流、JSON Schema 结构和 Java Bean 约束。
     *
     * @param toolsetVersion 工具集版本
     * @param workflowVersion 工作流版本
     * @param name 工具名
     * @param argumentsJson 原始模型参数
     * @return 已规范化调用
     */
    @SuppressWarnings("unchecked")
    public PreparedAssistantToolCall prepare(String toolsetVersion, String workflowVersion,
                                             String name, String argumentsJson) {
        AssistantTool<?> rawTool = requiredToolset(toolsetVersion).get(name);
        if (rawTool == null) {
            throw new AssistantToolException("TOOL_UNKNOWN", "模型请求了未注册工具");
        }
        if (!rawTool.descriptor().allowedWorkflowVersions().contains(workflowVersion)) {
            throw new AssistantToolException("TOOL_WORKFLOW_FORBIDDEN", "当前工作流不能调用该工具");
        }
        if (argumentsJson == null || argumentsJson.isBlank()
                || argumentsJson.length() > MAX_ARGUMENT_JSON_LENGTH) {
            throw new AssistantToolException("TOOL_ARGUMENT_INVALID", "工具参数为空或过长");
        }
        try {
            // Schema 层先拒绝非对象和 additionalProperties，再进入强类型 Bean Validation
            JsonNode root = objectMapper.readTree(argumentsJson);
            assertSchemaShape(rawTool.descriptor(), root);
            Object input = objectMapper.readerFor(rawTool.inputType())
                    .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .readValue(argumentsJson);
            Set<ConstraintViolation<Object>> violations = validator.validate(input);
            if (!violations.isEmpty()) {
                throw new AssistantToolException("TOOL_ARGUMENT_INVALID", "工具参数未通过业务约束");
            }
            String normalized = objectMapper.writeValueAsString(input);
            return new PreparedAssistantToolCall((AssistantTool<Object>) rawTool, input, normalized);
        } catch (AssistantToolException exception) {
            throw exception;
        } catch (JsonProcessingException exception) {
            throw new AssistantToolException("TOOL_ARGUMENT_INVALID", "工具参数不是合法 JSON", exception);
        }
    }

    /** 显式注册并拒绝名称冲突或描述不一致。 */
    private void register(Map<String, AssistantTool<?>> tools, AssistantTool<?> tool) {
        AssistantToolDescriptor descriptor = tool.descriptor();
        if (!descriptor.name().equals(descriptor.definition().name())
                || tools.putIfAbsent(descriptor.name(), tool) != null) {
            throw new IllegalStateException("客服工具注册冲突: " + descriptor.name());
        }
    }

    /** 读取已发布工具集。 */
    private Map<String, AssistantTool<?>> requiredToolset(String version) {
        Map<String, AssistantTool<?>> tools = toolsets.get(version);
        if (tools == null) {
            throw new AssistantToolException("TOOLSET_UNKNOWN", "客服工具集版本不存在");
        }
        return tools;
    }

    /** 按工具定义的 properties 执行首版 JSON Schema 结构门禁。 */
    private void assertSchemaShape(AssistantToolDescriptor descriptor, JsonNode root) {
        if (root == null || !root.isObject()) {
            throw new AssistantToolException("TOOL_ARGUMENT_INVALID", "工具参数必须是 JSON Object");
        }
        Object propertiesValue = descriptor.definition().inputSchema().get("properties");
        if (!(propertiesValue instanceof Map<?, ?> properties)) {
            throw new IllegalStateException("客服工具 Schema 缺少 properties");
        }
        root.fieldNames().forEachRemaining(field -> {
            if (!properties.containsKey(field)) {
                throw new AssistantToolException("TOOL_ARGUMENT_INVALID", "工具参数包含未知字段");
            }
        });
    }
}
