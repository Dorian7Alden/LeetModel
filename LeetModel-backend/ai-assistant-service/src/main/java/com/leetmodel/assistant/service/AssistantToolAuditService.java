package com.leetmodel.assistant.service;

import com.leetmodel.assistant.entity.AssistantToolCall;
import com.leetmodel.assistant.mapper.AssistantToolCallMapper;
import com.leetmodel.assistant.tool.AssistantToolDescriptor;
import com.leetmodel.assistant.tool.AssistantToolExecutionContext;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/** 维护客服工具调用不可覆盖的尝试记录和条件状态迁移。 */
@Service
public class AssistantToolAuditService {

    private final AssistantToolCallMapper mapper;

    public AssistantToolAuditService(AssistantToolCallMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 保存模型刚返回的工具调用事实。
     *
     * @param context 可信执行上下文
     * @param providerToolCallId 供应商调用标识
     * @param toolName 模型工具名
     * @param descriptor 已知工具描述，未知工具为 null
     * @param planningAiCallId 产生调用的 AI callId
     * @return 新审计记录
     */
    public AssistantToolCall receive(AssistantToolExecutionContext context,
                                     String providerToolCallId, String toolName,
                                     AssistantToolDescriptor descriptor,
                                     String planningAiCallId) {
        // RECEIVED 只保存身份、版本和关联标识，不保存未通过校验的原始参数
        LocalDateTime now = LocalDateTime.now();
        AssistantToolCall call = new AssistantToolCall();
        call.setConversationId(context.conversationId());
        call.setUserMessageId(context.userMessageId());
        call.setAssistantMessageId(context.assistantMessageId());
        call.setAttemptNo(context.attemptNo());
        call.setSequenceNo(context.sequenceNo());
        call.setProviderToolCallId(providerToolCallId);
        call.setToolsetVersion(context.toolsetVersion());
        call.setToolName(normalizeToolName(toolName));
        call.setToolVersion(descriptor == null ? "UNKNOWN" : descriptor.toolVersion());
        call.setStatus("RECEIVED");
        call.setPlanningAiCallId(planningAiCallId);
        call.setCreateTime(now);
        call.setUpdateTime(now);
        mapper.insert(call);
        return call;
    }

    /** 把已校验规范参数和执行开始时间原子写入。 */
    public void markRunning(AssistantToolCall call, String normalizedArgumentsJson) {
        // 只有 RECEIVED 可以取得执行权，避免重复执行同一审计记录
        LocalDateTime startedAt = LocalDateTime.now();
        if (mapper.markRunning(call.getId(), normalizedArgumentsJson, startedAt) != 1) {
            throw new IllegalStateException("客服工具调用未取得执行权");
        }
        call.setStatus("RUNNING");
        call.setArgumentsJson(normalizedArgumentsJson);
        call.setStartedAt(startedAt);
    }

    /** 把协议、白名单或参数错误记录为终态 REJECTED。 */
    public void reject(AssistantToolCall call, String errorCode) {
        // 拒绝只允许从 RECEIVED 进入终态
        LocalDateTime finishedAt = LocalDateTime.now();
        if (mapper.reject(call.getId(), errorCode, finishedAt) != 1) {
            throw new IllegalStateException("客服工具调用拒绝状态冲突");
        }
        call.setStatus("REJECTED");
        call.setErrorCode(errorCode);
        call.setFinishedAt(finishedAt);
    }

    /** 保存成功结果的最小快照、嵌套调用标识和耗时。 */
    public void complete(AssistantToolCall call, String resultSnapshotJson,
                         String nestedAiCallId, long durationMs) {
        // 完成只接受已经取得执行权的 RUNNING 记录
        LocalDateTime finishedAt = LocalDateTime.now();
        if (mapper.complete(call.getId(), resultSnapshotJson, nestedAiCallId,
                durationMs, finishedAt) != 1) {
            throw new IllegalStateException("客服工具调用完成状态冲突");
        }
        call.setStatus("COMPLETED");
        call.setResultSnapshotJson(resultSnapshotJson);
        call.setNestedAiCallId(nestedAiCallId);
        call.setDurationMs(durationMs);
        call.setFinishedAt(finishedAt);
    }

    /** 保存已开始调用的失败或超时终态。 */
    public void fail(AssistantToolCall call, String status, String errorCode, long durationMs) {
        // 状态参数严格收敛，避免 mapper 动态值扩大状态机
        if (!"FAILED".equals(status) && !"TIMED_OUT".equals(status)) {
            throw new IllegalArgumentException("客服工具失败状态不合法");
        }
        LocalDateTime finishedAt = LocalDateTime.now();
        if (mapper.fail(call.getId(), status, errorCode, durationMs, finishedAt) != 1) {
            throw new IllegalStateException("客服工具调用失败状态冲突");
        }
        call.setStatus(status);
        call.setErrorCode(errorCode);
        call.setDurationMs(durationMs);
        call.setFinishedAt(finishedAt);
    }

    /** 把模型工具名收敛到审计列边界，未知名仍保持可诊断。 */
    private String normalizeToolName(String toolName) {
        if (toolName == null || toolName.isBlank()) return "UNKNOWN";
        return toolName.length() <= 64 ? toolName : toolName.substring(0, 64);
    }
}
