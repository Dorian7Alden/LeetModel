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

/** 按用户明确条件筛选已发布题目候选的只读工具。 */
@Component
public class RecommendProblemTool implements AssistantTool<RecommendProblemInput> {

    private static final AssistantToolDescriptor DESCRIPTOR = new AssistantToolDescriptor(
            "recommend_problem", "RECOMMEND_PROBLEM_0001",
            new AiToolDefinition(AiToolType.FUNCTION, "recommend_problem",
                    "按用户明确给出的关键词、赛事、年份、难度、语言和时长条件筛选已发布题目；不代表个性化最优推荐。",
                    ProblemToolSchemas.recommend()),
            false, Duration.ofSeconds(3),
            Set.of("ASSISTANT_TOOLS_NO_RAG_V1", "ASSISTANT_TOOLS_RAG_V1"));

    private final ProblemFeignClient problemFeignClient;
    private final ProblemToolResultFactory resultFactory;

    public RecommendProblemTool(ProblemFeignClient problemFeignClient, ObjectMapper objectMapper) {
        this.problemFeignClient = problemFeignClient;
        this.resultFactory = new ProblemToolResultFactory(objectMapper);
    }

    @Override
    public AssistantToolDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public Class<RecommendProblemInput> inputType() {
        return RecommendProblemInput.class;
    }

    /** 根据确定性筛选条件查询已发布候选。 */
    @Override
    public AssistantToolOutput execute(RecommendProblemInput input,
                                       AssistantToolExecutionContext context) {
        // 规范化字符串后调用 problem-service 的唯一事实查询接口
        AssistantProblemQueryDTO request = new AssistantProblemQueryDTO(
                AssistantProblemQueryMode.RECOMMEND, null, trim(input.keyword()), false,
                trim(input.contestCode()), input.year(), input.difficulty(),
                trim(input.statementLanguage()), input.maxDurationMinutes(),
                input.limit() == null ? 3 : input.limit());
        Result<AssistantProblemResultDTO> response = problemFeignClient.queryForAssistant(request);
        if (response == null || !response.isSuccess() || response.getData() == null) {
            throw new AssistantToolException("PROBLEM_SERVICE_UNAVAILABLE", "题目查询服务暂不可用");
        }
        return resultFactory.create(response.getData());
    }

    /** 把可选字符串统一为去空白值。 */
    private String trim(String value) {
        return value == null ? null : value.trim();
    }
}
