package com.leetmodel.assistant.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leetmodel.assistant.entity.AssistantMessage;
import com.leetmodel.common.ai.client.AiClient;
import com.leetmodel.common.ai.model.AiChatRequest;
import com.leetmodel.common.ai.model.AiChatResponse;
import com.leetmodel.common.ai.model.AiFeatureCode;
import com.leetmodel.common.ai.model.AiModality;
import com.leetmodel.common.ai.model.AiOperationCode;
import com.leetmodel.common.ai.model.AiProvider;
import com.leetmodel.common.ai.model.AiRole;
import com.leetmodel.common.api.dto.ProblemOptionDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssistantWorkflowTest {

    @Mock
    private AiClient aiClient;

    private AssistantWorkflow workflow;

    @BeforeEach
    void setUp() throws Exception {
        workflow = new AssistantWorkflow(aiClient, new ObjectMapper());
    }

    @Test
    void onlySelectionIntentUsesProblemTool() {
        assertThat(workflow.needsProblemTool("帮我推荐题目")).isTrue();
        assertThat(workflow.needsProblemTool("recommend a problem")).isTrue();
        assertThat(workflow.needsProblemTool("如何上传 PDF？")).isFalse();
        assertThat(workflow.needsProblemTool(null)).isFalse();
    }

    @Test
    void injectsOnlyProvidedCandidatesIntoCurrentQuestion() throws Exception {
        AssistantMessage current = message(2L, "USER", "推荐一道入门题");
        when(aiClient.chat(any())).thenReturn(response("可以选择 101"));

        workflow.reply(List.of(message(1L, "ASSISTANT", "你好"), current), current,
                List.of(new ProblemOptionDTO(101L, 1001, "运输调度", 10L, 2026, "zh-CN", 1, 120)));

        ArgumentCaptor<AiChatRequest> captor = ArgumentCaptor.forClass(AiChatRequest.class);
        verify(aiClient).chat(captor.capture());
        AiChatRequest request = captor.getValue();
        assertThat(request.modality()).isEqualTo(AiModality.TEXT);
        assertThat(request.context().featureCode()).isEqualTo(AiFeatureCode.AI_ASSISTANT);
        assertThat(request.context().operationCode()).isEqualTo(AiOperationCode.CHAT_REPLY);
        assertThat(request.context().businessTaskId()).isEqualTo("message:2");
        assertThat(request.messages().get(0).role()).isEqualTo(AiRole.SYSTEM);
        assertThat(request.messages().get(0).content().get(0).text())
                .contains("不编造平台状态、题目或用户数据");
        assertThat(request.messages().get(1).content().get(0).text()).doesNotContain("101");
        assertThat(request.messages().get(2).content().get(0).text())
                .contains("只能依据这些数据推荐", "101", "运输调度");
    }

    @Test
    void emptyToolResultIsStillInjectedToPreventInventedProblems() throws Exception {
        AssistantMessage current = message(2L, "USER", "推荐题目");
        when(aiClient.chat(any())).thenReturn(response("当前没有可推荐题目"));

        workflow.reply(List.of(current), current, List.of());

        ArgumentCaptor<AiChatRequest> captor = ArgumentCaptor.forClass(AiChatRequest.class);
        verify(aiClient).chat(captor.capture());
        assertThat(captor.getValue().messages().get(1).content().get(0).text())
                .contains("只能依据这些数据推荐", "[]");
    }

    @Test
    void generalQuestionDoesNotReceiveProblemContext() throws Exception {
        AssistantMessage current = message(2L, "USER", "如何上传 PDF？");
        when(aiClient.chat(any())).thenReturn(response("进入提交页上传"));

        workflow.reply(List.of(current), current, null);

        ArgumentCaptor<AiChatRequest> captor = ArgumentCaptor.forClass(AiChatRequest.class);
        verify(aiClient).chat(captor.capture());
        assertThat(captor.getValue().messages().get(1).content().get(0).text())
                .isEqualTo("如何上传 PDF？");
    }

    @Test
    void rejectsBlankGatewayResponse() {
        AssistantMessage current = message(2L, "USER", "如何组队？");
        when(aiClient.chat(any())).thenReturn(response(" "));

        assertThatThrownBy(() -> workflow.reply(List.of(current), current, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("未返回客服回复");
    }

    private AssistantMessage message(Long id, String role, String content) {
        AssistantMessage message = new AssistantMessage();
        message.setId(id);
        message.setRole(role);
        message.setContent(content);
        return message;
    }

    private AiChatResponse response(String content) {
        return new AiChatResponse("call-1", AiProvider.NEW_API, "model-a", "provider-1",
                content, null, "stop", null);
    }
}
