package com.leetmodel.suggestion.workflow.v4;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leetmodel.common.api.dto.KnowledgeRetrievalResultDTO;
import com.leetmodel.common.api.dto.PaperParseDTO;
import com.leetmodel.common.api.dto.ProblemContextDTO;
import com.leetmodel.suggestion.entity.SuggestionTask;
import com.leetmodel.suggestion.service.evidence.ReviewEvidenceSnapshot;
import com.leetmodel.suggestion.workflow.SuggestionWorkflowResult;
import com.leetmodel.suggestion.workflow.v3.GroundedSuggestionV3Output;
import com.leetmodel.suggestion.workflow.v3.GroundedSuggestionV3Workflow;
import com.leetmodel.suggestion.workflow.v3.PromptTemplateRenderer;
import org.springframework.stereotype.Component;

/** 在 V3 动态建议流水线上装配 V4 三类方向报告。 */
@Component
public class GroundedSuggestionV4Workflow {

    public static final String VERSION = "GROUNDED_SUGGESTION_V4";
    public static final String RESULT_SCHEMA_VERSION = "GROUNDED_SUGGESTION_V4";
    public static final String MODEL_EXECUTION_CONFIG_VERSION = "MODEL_CFG_SUGGESTION_TEXT_0004";
    public static final String MODEL_NAME = "gemini-3.8-flash-high";

    private final GroundedSuggestionV3Workflow delegate;
    private final SuggestionV4ReportAssembler assembler;
    private final ObjectMapper objectMapper;
    private final String promptSnapshot;

    public GroundedSuggestionV4Workflow(
            GroundedSuggestionV3Workflow delegate,
            SuggestionV4ReportAssembler assembler,
            ObjectMapper objectMapper
    ) {
        this.delegate = delegate;
        this.assembler = assembler;
        this.objectMapper = objectMapper;
        this.promptSnapshot = PromptTemplateRenderer.loadClasspathPrompt(
                "prompts/phase3-suggestion-synthesizer-v4.st"
        );
    }

    public String currentPrompt() {
        return promptSnapshot;
    }

    public SuggestionWorkflowResult execute(
            SuggestionTask task,
            ProblemContextDTO problem,
            PaperParseDTO parse,
            ReviewEvidenceSnapshot reviewEvidence,
            KnowledgeRetrievalResultDTO knowledge
    ) throws Exception {
        SuggestionWorkflowResult v3Result = delegate.execute(
                task,
                problem,
                parse,
                reviewEvidence
        );
        GroundedSuggestionV3Output source = objectMapper.readValue(
                v3Result.resultJson(),
                GroundedSuggestionV3Output.class
        );
        GroundedSuggestionV4Output output = assembler.assemble(
                source,
                parse,
                reviewEvidence,
                knowledge
        );
        return new SuggestionWorkflowResult(
                objectMapper.writeValueAsString(output),
                v3Result.modelName(),
                v3Result.aiCallId()
        );
    }
}
