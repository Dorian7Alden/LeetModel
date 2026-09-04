package com.leetmodel.submission.controller;

import com.leetmodel.common.api.dto.SubmissionReviewDTO;
import com.leetmodel.common.api.dto.SubmissionSnapshotDTO;
import com.leetmodel.common.api.dto.SubmissionPreviewDTO;
import com.leetmodel.common.api.dto.ProblemSubmissionStatsDTO;
import com.leetmodel.common.core.result.Result;
import com.leetmodel.submission.service.SubmissionService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Validated
@RequestMapping("/internal/submissions")
@RequiredArgsConstructor
public class InternalSubmissionController {

    private final SubmissionService submissionService;

    /**
     * 获取指定提交的评审元数据与对象存储路径（供 ai-review-service 使用）。
     *
     * @param submissionId 目标提交记录 ID，不能为 null
     * @return 评审提交摘要 DTO
     */
    @Operation(summary = "获取评审提交摘要")
    @GetMapping("/{submissionId}")
    public Result<SubmissionReviewDTO> get(@PathVariable Long submissionId) {
        return Result.ok(submissionService.getForReview(submissionId));
    }

    /**
     * 查询已锁定的最终提交快照列表（供 ranking-service 榜单初始化）。
     *
     * @param problemId 可选的目标题目 ID 过滤条件
     * @return 最终提交快照列表
     */
    @Operation(summary = "查询最终提交快照")
    @GetMapping("/final")
    public Result<List<SubmissionSnapshotDTO>> listFinal(
            @RequestParam(required = false) Long problemId
    ) {
        return Result.ok(submissionService.listFinalSnapshots(problemId));
    }

    /**
     * 统计系统全量提交记录总数。
     *
     * @return 全量提交记录总数
     */
    @Operation(summary = "获取提交数量")
    @GetMapping("/count")
    public Result<Long> count() {
        return Result.ok(submissionService.count());
    }

    /**
     * 按题目维度统计所有成功提交的记录量。
     *
     * @return 题目提交统计 DTO 列表
     */
    @Operation(summary = "按题目统计成功提交量")
    @GetMapping("/stats/problems")
    public Result<List<ProblemSubmissionStatsDTO>> problemStats() {
        return Result.ok(submissionService.listProblemStats());
    }

    /**
     * 按时间倒序查询最近生成的提交快照列表。
     *
     * @param limit 单次拉取数量上限
     * @return 提交快照 DTO 列表
     */
    @Operation(summary = "查询最近提交快照")
    @GetMapping("/recent")
    public Result<List<SubmissionSnapshotDTO>> listRecent(
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "查询数量不能小于1")
            @Max(value = 100, message = "查询数量不能超过100") Integer limit) {
        return Result.ok(submissionService.listRecentSnapshots(limit));
    }

    /**
     * 获取指定提交记录的临时 PDF 在线预览下载地址。
     *
     * @param submissionId 目标提交记录 ID，不能为 null
     * @return 包含临时签名 URL 的预览 DTO
     */
    @Operation(summary = "获取提交 PDF 临时预览信息")
    @GetMapping("/{submissionId}/preview")
    public Result<SubmissionPreviewDTO> preview(@PathVariable Long submissionId) {
        return Result.ok(submissionService.getPreview(submissionId));
    }
}
