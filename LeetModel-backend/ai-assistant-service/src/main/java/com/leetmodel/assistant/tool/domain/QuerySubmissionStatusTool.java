package com.leetmodel.assistant.tool.domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leetmodel.assistant.tool.AssistantTool;
import com.leetmodel.assistant.tool.AssistantToolDescriptor;
import com.leetmodel.assistant.tool.AssistantToolException;
import com.leetmodel.assistant.tool.AssistantToolExecutionContext;
import com.leetmodel.assistant.tool.AssistantToolOutput;
import com.leetmodel.common.ai.model.AiToolDefinition;
import com.leetmodel.common.ai.model.AiToolType;
import com.leetmodel.common.api.dto.SubmissionSnapshotDTO;
import com.leetmodel.common.api.dto.TeamDTO;
import com.leetmodel.common.api.feign.SubmissionFeignClient;
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
 * 查询指定提交记录或用户队伍论文提交与评测状态的只读领域工具。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QuerySubmissionStatusTool implements AssistantTool<QuerySubmissionStatusTool.Input> {

    public record Input(Long teamId, Long submissionId) {}

    private static final AssistantToolDescriptor DESCRIPTOR = new AssistantToolDescriptor(
            "query_submission_status", "QUERY_SUBMISSION_STATUS_0001",
            new AiToolDefinition(AiToolType.FUNCTION, "query_submission_status",
                    "查询指定提交记录或当前队伍的论文提交状态、最新版本号、文件名及评审结果状态。",
                    Map.of(
                            "type", "object",
                            "properties", Map.of(
                                    "teamId", Map.of("type", "integer", "description", "指定队伍ID（可选）"),
                                    "submissionId", Map.of("type", "integer", "description", "指定提交记录ID（可选）")
                            ),
                            "additionalProperties", false
                    )),
            false, Duration.ofSeconds(3),
            Set.of("ASSISTANT_TOOLS_NO_RAG_V1", "ASSISTANT_TOOLS_RAG_V1", "ASSISTANT_TOOLS_RETRIEVAL_V1"));

    private final SubmissionFeignClient submissionFeignClient;
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
        Long targetTeamId = input.teamId();
        Long submissionId = input.submissionId();
        Map<String, Object> fact = new LinkedHashMap<>();

        try {
            SubmissionSnapshotDTO snapshot = null;
            if (submissionId != null) {
                Result<SubmissionSnapshotDTO> res = submissionFeignClient.getSubmissionSnapshot(submissionId);
                if (res != null && res.isSuccess()) snapshot = res.getData();
            } else {
                if (targetTeamId == null) {
                    Result<TeamDTO> teamRes = teamFeignClient.getUserCurrentTeam(context.userId());
                    if (teamRes != null && teamRes.isSuccess() && teamRes.getData() != null) {
                        targetTeamId = teamRes.getData().getId();
                    }
                }
                if (targetTeamId != null) {
                    Result<SubmissionSnapshotDTO> subRes = submissionFeignClient.getLatestTeamSubmission(targetTeamId);
                    if (subRes != null && subRes.isSuccess()) snapshot = subRes.getData();
                }
            }

            if (snapshot != null) {
                fact.put("hasSubmission", true);
                fact.put("submissionId", snapshot.getId());
                fact.put("teamId", snapshot.getTeamId());
                fact.put("problemId", snapshot.getProblemId());
                fact.put("version", snapshot.getVersion());
                fact.put("originalFilename", snapshot.getOriginalFilename());
                fact.put("status", snapshot.getStatus());
                fact.put("createTime", snapshot.getCreateTime() == null ? null : snapshot.getCreateTime().toString());
            } else {
                fact.put("hasSubmission", false);
                fact.put("message", "未查询到对应提交记录");
            }
            String json = objectMapper.writeValueAsString(fact);
            return AssistantToolOutput.data(json, json);
        } catch (JsonProcessingException e) {
            throw new AssistantToolException("TOOL_RESULT_INVALID", "提交状态无法序列化", e);
        } catch (Exception e) {
            log.warn("查询提交状态失败: {}", e.getMessage());
            throw new AssistantToolException("TOOL_EXECUTION_FAILED", "提交服务调用失败", e);
        }
    }
}
