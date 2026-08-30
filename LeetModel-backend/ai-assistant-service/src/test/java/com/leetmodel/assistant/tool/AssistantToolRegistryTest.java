package com.leetmodel.assistant.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leetmodel.assistant.tool.problem.RecommendProblemInput;
import com.leetmodel.assistant.tool.problem.RecommendProblemTool;
import com.leetmodel.assistant.tool.problem.SearchProblemInput;
import com.leetmodel.assistant.tool.problem.SearchProblemTool;
import com.leetmodel.common.api.feign.ProblemFeignClient;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class AssistantToolRegistryTest {

    private AssistantToolRegistry registry;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        ProblemFeignClient problemClient = mock(ProblemFeignClient.class);
        registry = new AssistantToolRegistry(objectMapper, validator,
                new SearchProblemTool(problemClient, objectMapper),
                new RecommendProblemTool(problemClient, objectMapper));
    }

    @Test
    void exposesOnlyExplicitV1ProblemToolsToToolWorkflow() {
        var definitions = registry.definitions(AssistantToolRegistry.TOOLSET_V1,
                "ASSISTANT_TOOLS_NO_RAG_V1");

        assertThat(definitions).extracting("name")
                .containsExactlyInAnyOrder("search_problem", "recommend_problem");
        assertThat(definitions).allSatisfy(definition -> {
            assertThat(definition.inputSchema()).containsEntry("type", "object")
                    .containsEntry("additionalProperties", false);
            assertThat(definition.name()).isEqualTo(definition.name().toLowerCase());
        });
    }

    @Test
    void rejectsUnknownToolsetAndOldWorkflow() {
        assertThatThrownBy(() -> registry.definitions("ASSISTANT_TOOLSET_9999",
                "ASSISTANT_TOOLS_NO_RAG_V1"))
                .isInstanceOf(AssistantToolException.class)
                .extracting("errorCode").isEqualTo("TOOLSET_UNKNOWN");
        assertThatThrownBy(() -> registry.definitions(AssistantToolRegistry.TOOLSET_V1,
                "ASSISTANT_NO_RAG_V1"))
                .isInstanceOf(AssistantToolException.class)
                .extracting("errorCode").isEqualTo("TOOLSET_WORKFLOW_MISMATCH");
    }

    @Test
    void preparesAndNormalizesValidSearchArguments() {
        PreparedAssistantToolCall prepared = registry.prepare(AssistantToolRegistry.TOOLSET_V1,
                "ASSISTANT_TOOLS_NO_RAG_V1", "search_problem",
                "{\"code\":1003,\"includeOverview\":true}");

        assertThat(prepared.input()).isEqualTo(new SearchProblemInput(1003, null, true, null));
        assertThat(prepared.normalizedArgumentsJson())
                .isEqualTo("{\"code\":1003,\"keyword\":null,\"includeOverview\":true,\"limit\":null}");
    }

    @Test
    void preparesValidRecommendationFilters() {
        PreparedAssistantToolCall prepared = registry.prepare(AssistantToolRegistry.TOOLSET_V1,
                "ASSISTANT_TOOLS_RAG_V1", "recommend_problem",
                "{\"keyword\":\"预测\",\"difficulty\":1,\"statementLanguage\":\"ZH\",\"limit\":3}");

        assertThat(prepared.input()).isEqualTo(new RecommendProblemInput(
                "预测", null, null, 1, "ZH", null, 3));
    }

    @Test
    void rejectsUnknownFieldsMalformedJsonAndSemanticViolations() {
        List<String> invalidArguments = List.of(
                "{\"code\":1003,\"userId\":7}",
                "{\"code\":1003,\"keyword\":\"预测\"}",
                "{\"keyword\":\"   \"}",
                "{\"code\":1003,\"limit\":2}",
                "[]",
                "not-json");

        assertThat(invalidArguments).allSatisfy(arguments -> assertThatThrownBy(
                () -> registry.prepare(AssistantToolRegistry.TOOLSET_V1,
                        "ASSISTANT_TOOLS_NO_RAG_V1", "search_problem", arguments))
                .isInstanceOf(AssistantToolException.class)
                .extracting("errorCode").isEqualTo("TOOL_ARGUMENT_INVALID"));
    }

    @Test
    void searchSchemaExpressesExclusiveLookupKeys() {
        var definition = registry.definitions(AssistantToolRegistry.TOOLSET_V1,
                        "ASSISTANT_TOOLS_NO_RAG_V1").stream()
                .filter(item -> "search_problem".equals(item.name())).findFirst().orElseThrow();

        assertThat(definition.inputSchema()).containsKey("oneOf");
        assertThat(((Map<?, ?>) definition.inputSchema().get("properties")).keySet()
                .stream().map(String::valueOf).toList())
                .contains("code", "keyword", "includeOverview", "limit");
    }
}
