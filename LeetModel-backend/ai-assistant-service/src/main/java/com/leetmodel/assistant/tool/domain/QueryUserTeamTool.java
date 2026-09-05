package com.leetmodel.assistant.tool.domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leetmodel.assistant.tool.AssistantTool;
import com.leetmodel.assistant.tool.AssistantToolDescriptor;
import com.leetmodel.assistant.tool.AssistantToolException;
import com.leetmodel.assistant.tool.AssistantToolExecutionContext;
import com.leetmodel.assistant.tool.AssistantToolOutput;
import com.leetmodel.common.ai.model.AiToolDefinition;
import com.leetmodel.common.ai.model.AiToolType;
import com.leetmodel.common.api.dto.TeamDTO;
import com.leetmodel.common.api.feign.TeamFeignClient;
import com.leetmodel.common.core.result.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 查询当前登录用户组队状态的只读领域工具。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QueryUserTeamTool implements AssistantTool<QueryUserTeamTool.Input> {

    public record Input() {}

    private static final AssistantToolDescriptor DESCRIPTOR = new AssistantToolDescriptor(
            "query_user_team", "QUERY_USER_TEAM_0001",
            new AiToolDefinition(AiToolType.FUNCTION, "query_user_team",
                    "查询当前登录用户所在队伍的组队状态、队伍名称、队长、成员数、实训状态及当前选题。",
                    Map.of("type", "object", "properties", Map.of(), "additionalProperties", false)),
            false, Duration.ofSeconds(3),
            Set.of("ASSISTANT_TOOLS_NO_RAG_V1", "ASSISTANT_TOOLS_RAG_V1", "ASSISTANT_TOOLS_RETRIEVAL_V1"));

    private final TeamFeignClient teamFeignClient;
    private final ObjectMapper objectMapper;

    @Override
    public AssistantToolDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public Class<Input> inputType() {
        return Input.class;
    }

    @Override
    public AssistantToolOutput execute(Input input, AssistantToolExecutionContext context) {
        Long userId = context.userId();
        Map<String, Object> fact = new LinkedHashMap<>();
        try {
            Result<TeamDTO> res = teamFeignClient.getUserCurrentTeam(userId);
            if (res != null && res.isSuccess() && res.getData() != null) {
                TeamDTO team = res.getData();
                fact.put("inTeam", true);
                fact.put("teamId", team.getId());
                fact.put("name", team.getName());
                fact.put("leaderId", team.getLeaderId());
                fact.put("memberCount", team.getMemberCount());
                fact.put("problemId", team.getProblemId());
                fact.put("practiceStatus", team.getPracticeStatus());
                fact.put("status", team.getStatus());
            } else {
                fact.put("inTeam", false);
                fact.put("message", "当前用户未加入任何队伍");
            }
            String json = objectMapper.writeValueAsString(fact);
            return AssistantToolOutput.data(json, json);
        } catch (JsonProcessingException e) {
            throw new AssistantToolException("TOOL_RESULT_INVALID", "队伍状态无法序列化", e);
        } catch (Exception e) {
            log.warn("查询用户队伍失败: {}", e.getMessage());
            throw new AssistantToolException("TOOL_EXECUTION_FAILED", "队伍服务调用失败", e);
        }
    }
}
