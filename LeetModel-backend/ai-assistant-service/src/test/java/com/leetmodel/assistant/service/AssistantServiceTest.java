package com.leetmodel.assistant.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leetmodel.assistant.entity.AssistantConversation;
import com.leetmodel.assistant.entity.AssistantMessage;
import com.leetmodel.assistant.mapper.AssistantConversationMapper;
import com.leetmodel.assistant.mapper.AssistantMessageMapper;
import com.leetmodel.assistant.vo.AssistantMessageVO;
import com.leetmodel.assistant.vo.AssistantReplyVO;
import com.leetmodel.assistant.workflow.AssistantWorkflow;
import com.leetmodel.common.ai.model.AiChatResponse;
import com.leetmodel.common.ai.model.AiProvider;
import com.leetmodel.common.api.dto.ProblemOptionDTO;
import com.leetmodel.common.api.feign.ProblemFeignClient;
import com.leetmodel.common.core.exception.BusinessException;
import com.leetmodel.common.core.result.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssistantServiceTest {

    private static final Long USER_ID = 7L;
    private static final Long CONVERSATION_ID = 101L;

    @Mock
    private AssistantConversationMapper conversationMapper;
    @Mock
    private AssistantMessageMapper messageMapper;
    @Mock
    private ProblemFeignClient problemFeignClient;
    @Mock
    private AssistantWorkflow workflow;

    private AssistantService service;

    @BeforeEach
    void setUp() {
        service = new AssistantService(conversationMapper, messageMapper, problemFeignClient,
                workflow, new ObjectMapper());
    }

    @Test
    void sendPersistsOneUserMessageAndOneCompletedReply() throws Exception {
        when(conversationMapper.selectOne(any())).thenReturn(conversation("ACTIVE"));
        when(messageMapper.selectOne(any())).thenReturn(null).thenReturn(null);
        assignMessageIds();
        when(messageMapper.selectList(any())).thenReturn(List.of());
        when(workflow.needsProblemTool("如何上传 PDF？")).thenReturn(false);
        when(workflow.reply(any(), any(), isNull())).thenReturn(response("进入提交页上传"));

        AssistantReplyVO result = service.send(CONVERSATION_ID, USER_ID,
                "  如何上传 PDF？  ", "request_001");

        assertThat(result.getUserMessage().getContent()).isEqualTo("如何上传 PDF？");
        assertThat(result.getAssistantMessage().getStatus()).isEqualTo("COMPLETED");
        assertThat(result.getAssistantMessage().getContent()).isEqualTo("进入提交页上传");
        verify(problemFeignClient, never()).getPublishedOptions(any(), any());
        verify(messageMapper).complete(anyLong(), any(), isNull(), any(), any(), any());
    }

    @Test
    void repeatedClientRequestReturnsExistingPairWithoutCallingAiAgain() throws Exception {
        AssistantMessage user = message(201L, "USER", "COMPLETED", "如何组队？");
        AssistantMessage reply = message(202L, "ASSISTANT", "COMPLETED", "打开队伍广场");
        reply.setReplyToMessageId(user.getId());
        when(conversationMapper.selectOne(any())).thenReturn(conversation("ACTIVE"));
        when(messageMapper.selectOne(any())).thenReturn(user, reply);

        AssistantReplyVO result = service.send(CONVERSATION_ID, USER_ID,
                "如何组队？", "request_001");

        assertThat(result.getAssistantMessage().getId()).isEqualTo(202L);
        verify(messageMapper, never()).insert(any(AssistantMessage.class));
        verify(workflow, never()).reply(any(), any(), any());
    }

    @Test
    void emptyProblemCandidatesArePassedToWorkflowAndRecordedAsToolUse() throws Exception {
        when(conversationMapper.selectOne(any())).thenReturn(conversation("ACTIVE"));
        when(messageMapper.selectOne(any())).thenReturn(null).thenReturn(null);
        assignMessageIds();
        when(messageMapper.selectList(any())).thenReturn(List.of());
        when(workflow.needsProblemTool("推荐题目")).thenReturn(true);
        when(problemFeignClient.getPublishedOptions(null, 8)).thenReturn(Result.ok(List.of()));
        when(workflow.reply(any(), any(), any())).thenReturn(response("当前没有候选题目"));

        AssistantReplyVO result = service.send(CONVERSATION_ID, USER_ID,
                "推荐题目", "request_002");

        assertThat(result.getAssistantMessage().getUsedProblemTool()).isTrue();
        verify(workflow).reply(any(), any(), org.mockito.ArgumentMatchers.eq(List.of()));
        verify(messageMapper).complete(anyLong(), any(), org.mockito.ArgumentMatchers.eq("[]"),
                any(), any(), any());
    }

    @Test
    void toolFailureBecomesRetryableAssistantMessageInsteadOfLosingQuestion() throws Exception {
        when(conversationMapper.selectOne(any())).thenReturn(conversation("ACTIVE"));
        when(messageMapper.selectOne(any())).thenReturn(null).thenReturn(null);
        assignMessageIds();
        when(workflow.needsProblemTool("推荐题目")).thenReturn(true);
        when(problemFeignClient.getPublishedOptions(null, 8)).thenReturn(null);

        AssistantReplyVO result = service.send(CONVERSATION_ID, USER_ID,
                "推荐题目", "request_003");

        assertThat(result.getAssistantMessage().getStatus()).isEqualTo("FAILED");
        assertThat(result.getAssistantMessage().getErrorMessage()).contains("题目查询服务暂不可用");
        verify(messageMapper).fail(anyLong(), any(), isNull(), any());
        verify(workflow, never()).reply(any(), any(), any());
    }

    @Test
    void aiFailureDoesNotExposeInternalAddressToUser() throws Exception {
        when(conversationMapper.selectOne(any())).thenReturn(conversation("ACTIVE"));
        when(messageMapper.selectOne(any())).thenReturn(null).thenReturn(null);
        assignMessageIds();
        when(messageMapper.selectList(any())).thenReturn(List.of());
        when(workflow.needsProblemTool("如何组队？")).thenReturn(false);
        when(workflow.reply(any(), any(), isNull()))
                .thenThrow(new IllegalStateException(
                        "POST http://localhost:8090/internal/ai/chat connection refused"));

        AssistantReplyVO result = service.send(CONVERSATION_ID, USER_ID,
                "如何组队？", "request_005");

        assertThat(result.getAssistantMessage().getStatus()).isEqualTo("FAILED");
        assertThat(result.getAssistantMessage().getErrorMessage())
                .isEqualTo("AI 客服暂时无法回答，请稍后重试")
                .doesNotContain("localhost", "/internal/");
    }

    @Test
    void conversationOwnershipIsCheckedBeforeMessageHistory() {
        when(conversationMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> service.getConversation(CONVERSATION_ID, USER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(40501);
        verify(messageMapper, never()).selectList(any());
    }

    @Test
    void closedConversationRejectsSendAndRetry() {
        when(conversationMapper.selectOne(any())).thenReturn(conversation("CLOSED"));

        assertThatThrownBy(() -> service.send(CONVERSATION_ID, USER_ID,
                "继续", "request_004"))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(40502);

        AssistantMessage failed = message(202L, "ASSISTANT", "FAILED", null);
        failed.setConversationId(CONVERSATION_ID);
        when(messageMapper.selectById(202L)).thenReturn(failed);
        assertThatThrownBy(() -> service.retry(202L, USER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(40502);
        verify(messageMapper, never()).claimRetry(anyLong(), any());
    }

    @Test
    void retryClaimsFailedReplyAndClearsFailureWithNewTrace() throws Exception {
        AssistantMessage failed = message(202L, "ASSISTANT", "FAILED", null);
        failed.setConversationId(CONVERSATION_ID);
        failed.setReplyToMessageId(201L);
        AssistantMessage user = message(201L, "USER", "COMPLETED", "如何组队？");
        when(messageMapper.selectById(202L)).thenReturn(failed);
        when(messageMapper.selectById(201L)).thenReturn(user);
        when(conversationMapper.selectOne(any())).thenReturn(conversation("ACTIVE"));
        when(messageMapper.claimRetry(anyLong(), any())).thenReturn(1);
        when(messageMapper.selectList(any())).thenReturn(List.of(user));
        when(workflow.needsProblemTool("如何组队？")).thenReturn(false);
        when(workflow.reply(any(), any(), isNull())).thenReturn(response("打开队伍广场"));

        AssistantMessageVO result = service.retry(202L, USER_ID);

        assertThat(result.getStatus()).isEqualTo("COMPLETED");
        assertThat(result.getErrorMessage()).isNull();
        assertThat(result.getAiCallId()).isEqualTo("call-1");
        verify(messageMapper).complete(org.mockito.ArgumentMatchers.eq(202L), any(), isNull(),
                any(), any(), any());
    }

    @Test
    void concurrentRetryClaimFailureDoesNotCallAi() throws Exception {
        AssistantMessage failed = message(202L, "ASSISTANT", "FAILED", null);
        failed.setConversationId(CONVERSATION_ID);
        when(messageMapper.selectById(202L)).thenReturn(failed);
        when(conversationMapper.selectOne(any())).thenReturn(conversation("ACTIVE"));
        when(messageMapper.claimRetry(anyLong(), any())).thenReturn(0);

        assertThatThrownBy(() -> service.retry(202L, USER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(40504);
        verify(workflow, never()).reply(any(), any(), any());
    }

    @Test
    void repeatedCloseIsIdempotentAndRecoveryCoversInterruptedGeneration() {
        when(conversationMapper.selectOne(any())).thenReturn(conversation("CLOSED"));
        when(messageMapper.selectList(any())).thenReturn(List.of());

        assertThat(service.close(CONVERSATION_ID, USER_ID).getStatus()).isEqualTo("CLOSED");
        verify(conversationMapper, never()).updateById(any(AssistantConversation.class));

        service.recoverStaleRetries();
        verify(messageMapper).recoverStaleRetries(any(LocalDateTime.class), any(LocalDateTime.class));
    }

    private void assignMessageIds() {
        doAnswer(invocation -> {
            AssistantMessage message = invocation.getArgument(0);
            message.setId("USER".equals(message.getRole()) ? 201L : 202L);
            return 1;
        }).when(messageMapper).insert(any(AssistantMessage.class));
    }

    private AssistantConversation conversation(String status) {
        AssistantConversation conversation = new AssistantConversation();
        conversation.setId(CONVERSATION_ID);
        conversation.setUserId(USER_ID);
        conversation.setTitle("新会话");
        conversation.setStatus(status);
        conversation.setCreateTime(LocalDateTime.now());
        conversation.setUpdateTime(LocalDateTime.now());
        return conversation;
    }

    private AssistantMessage message(Long id, String role, String status, String content) {
        AssistantMessage message = new AssistantMessage();
        message.setId(id);
        message.setConversationId(CONVERSATION_ID);
        message.setUserId(USER_ID);
        message.setRole(role);
        message.setStatus(status);
        message.setContent(content);
        message.setCreateTime(LocalDateTime.now());
        message.setUpdateTime(LocalDateTime.now());
        return message;
    }

    private AiChatResponse response(String content) {
        return new AiChatResponse("call-1", AiProvider.DEEPSEEK, "model-a", "provider-1",
                content, null, "stop", null);
    }
}
