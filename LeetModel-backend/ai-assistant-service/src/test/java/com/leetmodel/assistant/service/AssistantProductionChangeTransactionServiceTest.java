package com.leetmodel.assistant.service;

import com.leetmodel.assistant.entity.AssistantProductionAudit;
import com.leetmodel.assistant.entity.AssistantProductionChangeRequest;
import com.leetmodel.assistant.entity.AssistantProductionConfig;
import com.leetmodel.assistant.entity.AssistantProductionPointer;
import com.leetmodel.assistant.entity.AssistantWorkflowVersion;
import com.leetmodel.assistant.mapper.AssistantProductionAuditMapper;
import com.leetmodel.assistant.mapper.AssistantProductionChangeRequestMapper;
import com.leetmodel.assistant.mapper.AssistantProductionConfigMapper;
import com.leetmodel.assistant.mapper.AssistantProductionPointerMapper;
import com.leetmodel.assistant.mapper.AssistantWorkflowVersionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssistantProductionChangeTransactionServiceTest {

    @Mock AssistantProductionChangeRequestMapper changeMapper;
    @Mock AssistantProductionPointerMapper pointerMapper;
    @Mock AssistantProductionConfigMapper configMapper;
    @Mock AssistantWorkflowVersionMapper workflowMapper;
    @Mock AssistantProductionAuditMapper auditMapper;
    private AssistantProductionChangeTransactionService service;

    @BeforeEach
    void setUp() {
        service = new AssistantProductionChangeTransactionService(changeMapper, pointerMapper,
                configMapper, workflowMapper, auditMapper);
    }

    @Test
    void exactlyOneConditionalActivationProducesAudit() {
        AssistantProductionChangeRequest change = change("ACTIVATE");
        when(changeMapper.selectByRequestIdForUpdate("change1")).thenReturn(change);
        when(pointerMapper.selectByIdForUpdate(1L)).thenReturn(pointer(1L, 1L));
        when(configMapper.selectById(2L)).thenReturn(config(2L, "ASSISTANT_PROD_CFG_RAG"));
        when(workflowMapper.selectOne(any())).thenReturn(workflow());
        when(pointerMapper.activate(eq(1L), eq(2L), eq(1L), eq(7L), any(), any())).thenReturn(1);
        doAnswer(invocation -> {
            invocation.<AssistantProductionAudit>getArgument(0).setId(99L);
            return 1;
        }).when(auditMapper).insert(any(AssistantProductionAudit.class));

        var result = service.apply("change1", 7L, true);

        assertThat(result.getStatus()).isEqualTo("APPLIED");
        assertThat(result.getAuditId()).isEqualTo(99L);
        assertThat(result.getCurrent().getRevision()).isEqualTo(2L);
        verify(auditMapper).insert(any(AssistantProductionAudit.class));
    }

    @Test
    void staleRevisionBecomesConflictWithoutSuccessAudit() {
        AssistantProductionChangeRequest change = change("ACTIVATE");
        when(changeMapper.selectByRequestIdForUpdate("change1")).thenReturn(change);
        when(pointerMapper.selectByIdForUpdate(1L)).thenReturn(pointer(3L, 2L));
        when(configMapper.selectById(2L)).thenReturn(config(2L, "ASSISTANT_PROD_CFG_RAG"));
        when(configMapper.selectById(3L)).thenReturn(config(3L, "ASSISTANT_PROD_CFG_OTHER"));
        when(workflowMapper.selectOne(any())).thenReturn(workflow());
        when(pointerMapper.activate(eq(1L), eq(2L), eq(1L), eq(7L), any(), any())).thenReturn(0);

        var result = service.apply("change1", 7L, true);

        assertThat(result.getStatus()).isEqualTo("CONFLICT");
        assertThat(result.getCurrent().getProductionConfigVersion())
                .isEqualTo("ASSISTANT_PROD_CFG_OTHER");
        verify(auditMapper, never()).insert(any(AssistantProductionAudit.class));
    }

    @Test
    void rollbackUsesSameAtomicPointerProtocol() {
        AssistantProductionChangeRequest change = change("ROLLBACK");
        when(changeMapper.selectByRequestIdForUpdate("change1")).thenReturn(change);
        when(pointerMapper.selectByIdForUpdate(1L)).thenReturn(pointer(1L, 1L));
        when(configMapper.selectById(2L)).thenReturn(config(2L, "ASSISTANT_PROD_CFG_OLD"));
        when(workflowMapper.selectOne(any())).thenReturn(workflow());
        when(pointerMapper.activate(eq(1L), eq(2L), eq(1L), eq(7L), any(), any())).thenReturn(1);
        doAnswer(invocation -> {
            invocation.<AssistantProductionAudit>getArgument(0).setId(100L);
            return 1;
        }).when(auditMapper).insert(any(AssistantProductionAudit.class));

        assertThat(service.apply("change1", 7L, true).getStatus()).isEqualTo("APPLIED");
        verify(pointerMapper).activate(eq(1L), eq(2L), eq(1L), eq(7L), any(), any());
    }

    private AssistantProductionChangeRequest change(String action) {
        AssistantProductionChangeRequest change = new AssistantProductionChangeRequest();
        change.setChangeRequestId("change1");
        change.setAction(action);
        change.setExpectedRevision(1L);
        change.setSourceConfigId(1L);
        change.setTargetConfigId(2L);
        change.setOperatorId(7L);
        change.setReason("验证生产版本切换原因");
        change.setStatus("PENDING");
        change.setExpiresAt(LocalDateTime.now().plusMinutes(5));
        return change;
    }

    private AssistantProductionPointer pointer(Long configId, Long revision) {
        AssistantProductionPointer pointer = new AssistantProductionPointer();
        pointer.setId(1L);
        pointer.setActiveConfigId(configId);
        pointer.setRevision(revision);
        pointer.setActivatedBy(7L);
        pointer.setActivatedAt(LocalDateTime.now());
        pointer.setObservationUntil(LocalDateTime.now().plusMinutes(10));
        return pointer;
    }

    private AssistantProductionConfig config(Long id, String version) {
        AssistantProductionConfig config = new AssistantProductionConfig();
        config.setId(id);
        config.setProductionConfigVersion(version);
        config.setWorkflowVersion("ASSISTANT_RAG_V1");
        config.setPromptVersion("PROMPT_ASSISTANT_CHAT_0001");
        config.setModelExecutionConfigVersion("MODEL_CFG_ASSISTANT_TEXT_0001");
        config.setRagMode("FIXED_INDEX");
        config.setRagIndexVersion("rag-v1-test");
        return config;
    }

    private AssistantWorkflowVersion workflow() {
        AssistantWorkflowVersion workflow = new AssistantWorkflowVersion();
        workflow.setWorkflowVersion("ASSISTANT_RAG_V1");
        workflow.setName("固定索引RAG");
        workflow.setStatus("ENABLED");
        workflow.setImpactScope("新客服回复");
        return workflow;
    }
}
