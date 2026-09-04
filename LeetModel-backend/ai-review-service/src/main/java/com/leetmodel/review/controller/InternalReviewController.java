package com.leetmodel.review.controller;

import com.leetmodel.common.api.dto.ReviewSummaryDTO;
import com.leetmodel.common.api.dto.ReviewExperimentRequestDTO;
import com.leetmodel.common.api.dto.ReviewExperimentResultDTO;
import com.leetmodel.common.api.dto.ReviewVersionDTO;
import com.leetmodel.common.api.dto.AiFeatureDefinitionDTO;
import com.leetmodel.common.api.dto.AiExperimentRequestDTO;
import com.leetmodel.common.api.dto.AiExperimentResultDTO;
import com.leetmodel.common.api.dto.PaperParseDTO;
import com.leetmodel.common.core.result.Result;
import com.leetmodel.review.parse.PaperParseService;
import com.leetmodel.review.service.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

@RestController
@Validated
@RequestMapping("/internal/reviews")
@RequiredArgsConstructor
public class InternalReviewController {

    private final ReviewService reviewService;
    private final PaperParseService paperParseService;

    /**
     * 创建默认生产版本的论文 AI 评审任务。
     *
     * @param submissionId 提交记录 ID，不能为 null
     * @param teamId       队伍 ID，不能为 null
     * @param problemId    题目 ID，不能为 null
     * @return 评审任务 ID
     */
    @Operation(summary = "创建基础评审任务")
    @PostMapping("/tasks")
    public Result<Long> create(@RequestParam Long submissionId, @RequestParam Long teamId, @RequestParam Long problemId) {
        return Result.ok(reviewService.createTask(submissionId, teamId, problemId));
    }

    /**
     * 按指定的特定工作流版本创建论文评审任务。
     *
     * @param submissionId    提交记录 ID，不能为 null
     * @param teamId          队伍 ID，不能为 null
     * @param problemId       题目 ID，不能为 null
     * @param workflowVersion 目标工作流版本号，不能为 null
     * @return 评审任务 ID
     */
    @Operation(summary = "按指定不可变评审版本创建任务")
    @PostMapping("/tasks/versioned")
    public Result<Long> createVersioned(@RequestParam Long submissionId,
                                        @RequestParam Long teamId,
                                        @RequestParam Long problemId,
                                        @RequestParam String workflowVersion) {
        return Result.ok(reviewService.createTask(submissionId, teamId, problemId, workflowVersion));
    }

    /**
     * 根据任务 ID 查询评审结果摘要。
     *
     * @param taskId 评审任务 ID，不能为 null
     * @return 评审摘要 DTO
     */
    @Operation(summary = "按任务查询评审摘要")
    @GetMapping("/tasks/{taskId}")
    public Result<ReviewSummaryDTO> getByTask(@PathVariable Long taskId) {
        return Result.ok(reviewService.getSummaryByTask(taskId));
    }

    /**
     * 幂等确保指定提交对应版本的 PDF 结构化解析产物已就绪。
     *
     * @param submissionId    提交记录 ID，不能为 null
     * @param workflowVersion 工作流版本号，不能为 null
     * @return 论文结构化解析结果 DTO
     */
    @Operation(summary = "确保指定版本的 PDF 解析产物存在")
    @PostMapping("/parses/{submissionId}/ensure")
    public Result<PaperParseDTO> ensureParse(@PathVariable Long submissionId,
                                             @RequestParam String workflowVersion) {
        return Result.ok(paperParseService.ensure(submissionId, workflowVersion));
    }

    /**
     * 根据提交记录 ID 查询关联的评审结果摘要。
     *
     * @param submissionId 提交记录 ID，不能为 null
     * @return 评审摘要 DTO
     */
    @Operation(summary = "按提交查询评审摘要")
    @GetMapping("/submissions/{submissionId}")
    public Result<ReviewSummaryDTO> getBySubmission(@PathVariable Long submissionId) {
        return Result.ok(reviewService.getSummaryBySubmission(submissionId));
    }

    /**
     * 查询所有已完成评审且具有有效成绩的评审摘要（供榜单服务重建排行）。
     *
     * @param problemId 可选的题目 ID 过滤
     * @return 评审摘要 DTO 列表
     */
    @Operation(summary = "查询已完成评审摘要")
    @GetMapping("/completed")
    public Result<List<ReviewSummaryDTO>> listCompleted(
            @RequestParam(required = false) Long problemId
    ) {
        return Result.ok(reviewService.listCompletedSummaries(problemId));
    }

    /**
     * 统计系统全量评审任务总数。
     *
     * @return 评审任务总数
     */
    @Operation(summary = "获取评审任务数量")
    @GetMapping("/count")
    public Result<Long> count() {
        return Result.ok(reviewService.count());
    }

    /**
     * 按时间倒序查询最近的评审任务摘要列表。
     *
     * @param limit 单次拉取数量上限
     * @return 评审摘要 DTO 列表
     */
    @Operation(summary = "查询最近评审任务")
    @GetMapping("/recent")
    public Result<List<ReviewSummaryDTO>> listRecent(
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "查询数量不能小于1")
            @Max(value = 100, message = "查询数量不能超过100") Integer limit) {
        return Result.ok(reviewService.listRecentSummaries(limit));
    }

    /**
     * 在隔离沙箱中执行单次指定版本的评审实验（不影响当前成绩与榜单）。
     *
     * @param request 实验请求参数对象，不能为 null
     * @return 实验结果 DTO
     */
    @Operation(summary = "执行隔离评审实验")
    @PostMapping("/experiments")
    public Result<ReviewExperimentResultDTO> runExperiment(
            @Valid @RequestBody ReviewExperimentRequestDTO request) {
        return Result.ok(reviewService.runExperiment(
                request.getSubmissionId(), request.getWorkflowVersion()));
    }

    /**
     * 使用跨功能通用协议执行隔离评审实验（供评测系统调度）。
     *
     * @param request 通用实验请求对象，不能为 null
     * @return 通用实验结果 DTO
     */
    @Operation(summary = "使用通用契约执行隔离评审实验")
    @PostMapping("/experiments/v2")
    public Result<AiExperimentResultDTO> runExperimentV2(
            @Valid @RequestBody AiExperimentRequestDTO request) {
        return Result.ok(reviewService.runExperiment(request));
    }

    /**
     * 查询已注册的全部评审算法与工作流版本列表。
     *
     * @return 评审版本 DTO 列表
     */
    @Operation(summary = "查询评审版本")
    @GetMapping("/versions")
    public Result<List<ReviewVersionDTO>> listVersions() {
        return Result.ok(reviewService.listVersions());
    }

    /**
     * 获取评审功能的元数据定义与候选工作流版本目录。
     *
     * @return 功能定义 DTO
     */
    @Operation(summary = "查询 AI 评审功能与工作流版本目录")
    @GetMapping("/feature-definition")
    public Result<AiFeatureDefinitionDTO> getFeatureDefinition() {
        return Result.ok(reviewService.getFeatureDefinition());
    }
}
