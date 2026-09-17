package com.leetmodel.review.workflow.v4;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leetmodel.common.api.dto.DeepEvidenceReviewV3Output;
import com.leetmodel.common.api.dto.DeepEvidenceReviewV4Output;
import com.leetmodel.common.api.dto.PaperParseDTO;
import com.leetmodel.common.api.dto.ProblemContextDTO;
import com.leetmodel.common.api.dto.SubmissionReviewDTO;
import com.leetmodel.common.api.feign.ProblemFeignClient;
import com.leetmodel.common.core.result.Result;
import com.leetmodel.review.entity.ReviewTask;
import com.leetmodel.review.parse.PaperParseService;
import com.leetmodel.review.parse.v2.PaperDocumentV2;
import com.leetmodel.review.parse.v2.PaperParseV2Parser;
import com.leetmodel.review.workflow.ReviewWorkflow;
import com.leetmodel.review.workflow.ReviewWorkflowResult;
import com.leetmodel.review.workflow.v3.DeepEvidenceReviewV3Workflow;
import com.leetmodel.review.workflow.v3.PromptTemplateRenderer;
import org.springframework.stereotype.Component;

/** 在 V3 科学评分流水线上装配专业 Markdown 证据报告。 */
@Component
public class DeepEvidenceReviewV4Workflow implements ReviewWorkflow {

    public static final String VERSION_CODE = "DEEP_EVIDENCE_REVIEW_V4";
    public static final long VERSION_ID = 4L;
    public static final String RESULT_SCHEMA_VERSION = "DEEP_EVIDENCE_REVIEW_V4";
    public static final String SCORING_RULE_VERSION = "MODELING_TRAINING_RUBRIC_V4";
    public static final String MODEL_EXECUTION_CONFIG_VERSION = "MODEL_CFG_REVIEW_TEXT_0004";
    public static final String MODEL_NAME = "gemini-3.8-flash-high";

    private final DeepEvidenceReviewV3Workflow delegate;
    private final PaperParseService parseService;
    private final ProblemFeignClient problemClient;
    private final ObjectMapper objectMapper;
    private final ReviewV4ReportAssembler assembler;
    private final String promptSnapshot;

    public DeepEvidenceReviewV4Workflow(
            DeepEvidenceReviewV3Workflow delegate,
            PaperParseService parseService,
            ProblemFeignClient problemClient,
            ObjectMapper objectMapper,
            ReviewV4ReportAssembler assembler
    ) {
        this.delegate = delegate;
        this.parseService = parseService;
        this.problemClient = problemClient;
        this.objectMapper = objectMapper;
        this.assembler = assembler;
        this.promptSnapshot = PromptTemplateRenderer.loadClasspathPrompt(
                "prompts/phase1-structural-review-v4.st"
        );
    }

    @Override
    public String versionCode() {
        return VERSION_CODE;
    }

    @Override
    public Long versionId() {
        return VERSION_ID;
    }

    @Override
    public String currentPrompt() {
        return promptSnapshot;
    }

    @Override
    public ReviewWorkflowResult execute(
            ReviewTask task,
            SubmissionReviewDTO submission
    ) throws Exception {
        ReviewWorkflowResult scientificResult = delegate.execute(task, submission);
        DeepEvidenceReviewV3Output source = objectMapper.readValue(
                scientificResult.resultJson(),
                DeepEvidenceReviewV3Output.class
        );
        PaperParseDTO parse = parseService.ensure(
                submission.getId(),
                PaperParseV2Parser.WORKFLOW_VERSION
        );
        PaperDocumentV2 document = objectMapper.readValue(
                parse.getDocumentJson(),
                PaperDocumentV2.class
        );
        Result<ProblemContextDTO> problemResult = problemClient.getProblemContext(
                submission.getProblemId()
        );
        if (problemResult == null || !problemResult.isSuccess() || problemResult.getData() == null) {
            throw new IllegalStateException("problem-service 暂不可用");
        }
        DeepEvidenceReviewV4Output output = assembler.assemble(
                source,
                document,
                problemResult.getData()
        );
        return new ReviewWorkflowResult(
                output.getScore(),
                objectMapper.writeValueAsString(output),
                scientificResult.modelName(),
                scientificResult.aiCallId(),
                parse.getArtifactId()
        );
    }
}
