package com.leetmodel.assistant.service;

import com.leetmodel.assistant.entity.AssistantProductionConfig;
import com.leetmodel.assistant.entity.AssistantProductionChangeRequest;
import com.leetmodel.assistant.entity.AssistantProductionPointer;
import com.leetmodel.assistant.entity.AssistantWorkflowVersion;
import com.leetmodel.assistant.mapper.AssistantProductionAuditMapper;
import com.leetmodel.assistant.mapper.AssistantProductionChangeRequestMapper;
import com.leetmodel.assistant.mapper.AssistantProductionConfigMapper;
import com.leetmodel.assistant.mapper.AssistantProductionPointerMapper;
import com.leetmodel.assistant.mapper.AssistantWorkflowVersionMapper;
import com.leetmodel.common.api.dto.AssistantProductionChangePreviewRequestDTO;
import com.leetmodel.common.core.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssistantProductionConfigServiceTest {

    @Mock AssistantWorkflowVersionMapper workflowMapper;
    @Mock AssistantProductionConfigMapper configMapper;
    @Mock AssistantProductionPointerMapper pointerMapper;
    @Mock AssistantProductionChangeRequestMapper changeMapper;
    @Mock AssistantProductionAuditMapper auditMapper;
    @Mock AssistantProductionDependencyValidator validator;
    @Mock AssistantProductionChangeTransactionService transactionService;
    private AssistantProductionConfigService service;

    @BeforeEach
    void setUp() {
        service = new AssistantProductionConfigService(workflowMapper, configMapper, pointerMapper,
                changeMapper, auditMapper, validator, transactionService);
    }

    @Test
    void previewFreezesRevisionOperatorReasonAndExactRagTarget() {
        AssistantProductionConfig current = config(1L, "ASSISTANT_PROD_CFG_0001",
                "ASSISTANT_NO_RAG_V1", "NONE", null);
        AssistantProductionConfig target = config(2L, "ASSISTANT_PROD_CFG_RAG",
                "ASSISTANT_RAG_V1", "FIXED_INDEX", "rag-v1-test");
        when(pointerMapper.selectById(1L)).thenReturn(pointer(1L, 3L));
        when(configMapper.selectById(1L)).thenReturn(current);
        when(workflowMapper.selectOne(any())).thenReturn(
                workflow("ASSISTANT_NO_RAG_V1", "NONE"),
                workflow("ASSISTANT_RAG_V1", "FIXED_INDEX"));
        when(configMapper.selectOne(any())).thenReturn(target);
        when(validator.isReady(target)).thenReturn(true);

        var preview = service.preview(new AssistantProductionChangePreviewRequestDTO(
                "ACTIVATE", 3L, "ASSISTANT_RAG_V1", null,
                "rag-v1-test", "启用固定知识索引进行受控观察", 7L));

        assertThat(preview.getStatus()).isEqualTo("PENDING");
        assertThat(preview.getExpectedRevision()).isEqualTo(3L);
        assertThat(preview.getTarget().getRagIndexVersion()).isEqualTo("rag-v1-test");
        assertThat(preview.getDifferences()).anyMatch(item -> item.contains("RAG模式"));
        verify(changeMapper).insert(any(AssistantProductionChangeRequest.class));
    }

    @Test
    void unavailableDependencyRejectsPreviewBeforePointerMutation() {
        AssistantProductionConfig current = config(1L, "ASSISTANT_PROD_CFG_0001",
                "ASSISTANT_NO_RAG_V1", "NONE", null);
        AssistantProductionConfig target = config(2L, "ASSISTANT_PROD_CFG_RAG",
                "ASSISTANT_RAG_V1", "FIXED_INDEX", "rag-v1-test");
        when(pointerMapper.selectById(1L)).thenReturn(pointer(1L, 1L));
        when(configMapper.selectById(1L)).thenReturn(current);
        when(workflowMapper.selectOne(any())).thenReturn(
                workflow("ASSISTANT_NO_RAG_V1", "NONE"),
                workflow("ASSISTANT_RAG_V1", "FIXED_INDEX"));
        when(configMapper.selectOne(any())).thenReturn(target);
        when(validator.isReady(target)).thenReturn(false);

        assertThatThrownBy(() -> service.preview(new AssistantProductionChangePreviewRequestDTO(
                "ACTIVATE", 1L, "ASSISTANT_RAG_V1", null,
                "rag-v1-test", "验证不可用索引会被服务端拒绝", 7L)))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getCode()).isEqualTo(50501));
    }

    @Test
    void reasonMustRemainMeaningfulAfterTrimming() {
        assertThatThrownBy(() -> service.preview(new AssistantProductionChangePreviewRequestDTO(
                "ACTIVATE", 1L, "ASSISTANT_RAG_V1", null,
                "rag-v1-test", "原因      ", 7L)))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getCode()).isEqualTo(40506));
    }

    @Test
    void configListSeparatesCurrentHistoryFromNeverAppliedPreview() {
        AssistantProductionConfig current = config(1L, "ASSISTANT_PROD_CFG_0001",
                "ASSISTANT_NO_RAG_V1", "NONE", null);
        AssistantProductionConfig previewOnly = config(2L, "ASSISTANT_PROD_CFG_PREVIEW",
                "ASSISTANT_RAG_V1", "FIXED_INDEX", "rag-v1-test");
        when(pointerMapper.selectById(1L)).thenReturn(pointer(1L, 3L));
        when(configMapper.selectById(1L)).thenReturn(current);
        when(configMapper.selectList(any())).thenReturn(List.of(previewOnly, current));
        when(workflowMapper.selectOne(any())).thenReturn(
                workflow("ASSISTANT_NO_RAG_V1", "NONE"),
                workflow("ASSISTANT_RAG_V1", "FIXED_INDEX"),
                workflow("ASSISTANT_NO_RAG_V1", "NONE"));
        when(auditMapper.selectCount(any())).thenReturn(0L);

        var configs = service.listConfigs(20);

        assertThat(configs).extracting(item -> item.getProductionConfigVersion()
                        + ":" + item.getEverActive())
                .containsExactly("ASSISTANT_PROD_CFG_PREVIEW:false",
                        "ASSISTANT_PROD_CFG_0001:true");
    }

    @Test
    void toolActivationFreezesToolsetAndShowsItInPreviewDifference() {
        AssistantProductionConfig current = config(1L, "ASSISTANT_PROD_CFG_0001",
                "ASSISTANT_NO_RAG_V1", "NONE", null);
        AssistantProductionConfig target = config(3L, "ASSISTANT_PROD_CFG_TOOLS",
                "ASSISTANT_TOOLS_NO_RAG_V1", "NONE", null);
        target.setPromptVersion("PROMPT_ASSISTANT_TOOLS_0001");
        target.setModelExecutionConfigVersion("MODEL_CFG_ASSISTANT_TOOLS_0001");
        target.setToolsetVersion("ASSISTANT_TOOLSET_0001");
        when(pointerMapper.selectById(1L)).thenReturn(pointer(1L, 4L));
        when(configMapper.selectById(1L)).thenReturn(current);
        when(workflowMapper.selectOne(any())).thenReturn(
                workflow("ASSISTANT_NO_RAG_V1", "NONE"),
                toolWorkflow("ASSISTANT_TOOLS_NO_RAG_V1", "NONE"));
        when(configMapper.selectOne(any())).thenReturn(target);
        when(validator.isReady(target)).thenReturn(true);

        var preview = service.preview(new AssistantProductionChangePreviewRequestDTO(
                "ACTIVATE", 4L, "ASSISTANT_TOOLS_NO_RAG_V1", null,
                null, "启用受控工具工作流进行生产观察", 7L));

        assertThat(preview.getTarget().getToolsetVersion())
                .isEqualTo("ASSISTANT_TOOLSET_0001");
        assertThat(preview.getDifferences())
                .anyMatch(item -> item.contains("工具集版本")
                        && item.contains("ASSISTANT_TOOLSET_0001"));
    }

    private AssistantProductionPointer pointer(Long configId, Long revision) {
        AssistantProductionPointer pointer = new AssistantProductionPointer();
        pointer.setId(1L);
        pointer.setActiveConfigId(configId);
        pointer.setRevision(revision);
        pointer.setActivatedBy(0L);
        pointer.setActivatedAt(LocalDateTime.now());
        pointer.setObservationUntil(LocalDateTime.now());
        return pointer;
    }

    private AssistantProductionConfig config(Long id, String configVersion,
                                             String workflowVersion, String ragMode,
                                             String ragIndexVersion) {
        AssistantProductionConfig config = new AssistantProductionConfig();
        config.setId(id);
        config.setProductionConfigVersion(configVersion);
        config.setWorkflowVersion(workflowVersion);
        config.setPromptVersion("PROMPT_ASSISTANT_CHAT_0001");
        config.setModelExecutionConfigVersion("MODEL_CFG_ASSISTANT_TEXT_0001");
        config.setRagMode(ragMode);
        config.setRagIndexVersion(ragIndexVersion);
        return config;
    }

    private AssistantWorkflowVersion workflow(String version, String ragMode) {
        AssistantWorkflowVersion workflow = new AssistantWorkflowVersion();
        workflow.setWorkflowVersion(version);
        workflow.setName(version);
        workflow.setStatus("ENABLED");
        workflow.setPromptVersion("PROMPT_ASSISTANT_CHAT_0001");
        workflow.setModelExecutionConfigVersion("MODEL_CFG_ASSISTANT_TEXT_0001");
        workflow.setRagMode(ragMode);
        workflow.setInputSchema("ASSISTANT_QUESTION_V1");
        workflow.setOutputSchema("ASSISTANT_REPLY_V1");
        workflow.setCompatibility("历史可读");
        workflow.setImpactScope("只影响新回复");
        workflow.setExperimentCandidate(true);
        return workflow;
    }

    private AssistantWorkflowVersion toolWorkflow(String version, String ragMode) {
        AssistantWorkflowVersion workflow = workflow(version, ragMode);
        workflow.setPromptVersion("PROMPT_ASSISTANT_TOOLS_0001");
        workflow.setModelExecutionConfigVersion("MODEL_CFG_ASSISTANT_TOOLS_0001");
        workflow.setToolsetVersion("ASSISTANT_TOOLSET_0001");
        return workflow;
    }
}
