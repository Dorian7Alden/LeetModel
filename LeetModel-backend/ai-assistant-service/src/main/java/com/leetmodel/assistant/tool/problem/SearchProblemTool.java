package com.leetmodel.assistant.tool.problem;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leetmodel.assistant.tool.AssistantTool;
import com.leetmodel.assistant.tool.AssistantToolDescriptor;
import com.leetmodel.assistant.tool.AssistantToolException;
import com.leetmodel.assistant.tool.AssistantToolExecutionContext;
import com.leetmodel.assistant.tool.AssistantToolOutput;
import com.leetmodel.common.ai.model.AiToolDefinition;
import com.leetmodel.common.ai.model.AiToolType;
import com.leetmodel.common.api.dto.AssistantProblemQueryDTO;
import com.leetmodel.common.api.dto.AssistantProblemQueryMode;
import com.leetmodel.common.api.dto.AssistantProblemResultDTO;
import com.leetmodel.common.api.feign.ProblemFeignClient;
import com.leetmodel.common.core.result.Result;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Set;

/** 按题号或标题关键词查询已发布题目的只读工具。 */
@Component
public class SearchProblemTool implements AssistantTool<SearchProblemInput> {

    private static final AssistantToolDescriptor DESCRIPTOR = new AssistantToolDescriptor(
            "search_problem", "SEARCH_PROBLEM_0001",
            new AiToolDefinition(AiToolType.FUNCTION, "search_problem",
                    "查询指定题号或标题关键词对应的已发布题目。询问题面、难度、年份、赛事、语言或建议时长时使用。",
                    ProblemToolSchemas.search()),
            false, Duration.ofSeconds(3),
            Set.of("ASSISTANT_TOOLS_NO_RAG_V1", "ASSISTANT_TOOLS_RAG_V1"));

    private final ProblemFeignClient problemFeignClient;
    private final ProblemToolResultFactory resultFactory;

    public SearchProblemTool(ProblemFeignClient problemFeignClient, ObjectMapper objectMapper) {
        this.problemFeignClient = problemFeignClient;
        this.resultFactory = new ProblemToolResultFactory(objectMapper);
    }

    @Override
    public AssistantToolDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public Class<SearchProblemInput> inputType() {
        return SearchProblemInput.class;
    }

    /** 查询已发布题目并返回字段白名单结果。 */
    @Override
    public AssistantToolOutput execute(SearchProblemInput input,
                                       AssistantToolExecutionContext context) {
        // 只把模型可控的查询字段映射给数据所有者，可信身份不进入请求
        String keyword = input.keyword() == null ? null : input.keyword().trim();
        AssistantProblemQueryDTO request = new AssistantProblemQueryDTO(
                AssistantProblemQueryMode.SEARCH, input.code(), keyword,
                Boolean.TRUE.equals(input.includeOverview()), null, null, null, null,
                null, input.code() == null ? defaultLimit(input.limit(), 5) : 1);
        Result<AssistantProblemResultDTO> response = problemFeignClient.queryForAssistant(request);
        if (response == null || !response.isSuccess() || response.getData() == null) {
            throw new AssistantToolException("PROBLEM_SERVICE_UNAVAILABLE", "题目查询服务暂不可用");
        }
        return resultFactory.create(response.getData());
    }

    /** 使用受控默认值并保留已校验的显式限制。 */
    private int defaultLimit(Integer limit, int defaultValue) {
        return limit == null ? defaultValue : limit;
    }
}
