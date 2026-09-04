package com.leetmodel.submission.controller;

import com.leetmodel.common.core.result.Result;
import com.leetmodel.common.security.context.UserContext;
import com.leetmodel.submission.dto.UploadInitializeRequest;
import com.leetmodel.submission.service.SubmissionService;
import com.leetmodel.submission.service.SubmissionUploadService;
import com.leetmodel.submission.vo.SubmissionVO;
import com.leetmodel.submission.vo.UploadSessionVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

@RestController
@RequestMapping("/api/submissions")
@RequiredArgsConstructor
@Tag(name = "论文提交")
public class SubmissionController {
    private final SubmissionService submissionService;
    private final SubmissionUploadService uploadService;

    /**
     * 初始化分片上传会话，校验队伍提交窗口并返回会话元数据。
     *
     * @param request 包含文件大小、SHA-256 和队伍 ID 的初始化请求，不能为 null
     * @return 分片上传会话视图对象
     */
    @Operation(summary = "初始化或恢复 PDF 分片上传")
    @PostMapping("/uploads")
    public Result<UploadSessionVO> initializeUpload(
            @Valid @RequestBody UploadInitializeRequest request
    ) {
        return Result.ok(uploadService.initialize(request, UserContext.getUserId()));
    }

    /**
     * 查询指定分片上传会话的进度与已上传分片索引。
     *
     * @param uploadId 上传会话全局 Token，不能为 null
     * @return 分片上传会话视图对象
     */
    @Operation(summary = "查询 PDF 分片上传状态")
    @GetMapping("/uploads/{uploadId}")
    public Result<UploadSessionVO> getUpload(@PathVariable String uploadId) {
        return Result.ok(uploadService.get(uploadId, UserContext.getUserId()));
    }

    /**
     * 上传单个 PDF 二进制分片并执行 SHA-256 完整性校验。
     *
     * @param uploadId   上传会话全局 Token，不能为 null
     * @param chunkIndex 当前分片序号（从 0 开始），不能为 null
     * @param sha256     分片 SHA-256 校验摘要，不能为 null
     * @param file       分片二进制文件，不能为 null
     * @return 更新后的上传会话视图对象
     */
    @Operation(summary = "上传 PDF 分片")
    @PutMapping(path = "/uploads/{uploadId}/chunks/{chunkIndex}", consumes = "multipart/form-data")
    public Result<UploadSessionVO> uploadChunk(
            @PathVariable String uploadId,
            @PathVariable Integer chunkIndex,
            @RequestParam String sha256,
            @RequestPart("file") MultipartFile file
    ) {
        return Result.ok(uploadService.uploadChunk(
                uploadId, chunkIndex, sha256, file, UserContext.getUserId()
        ));
    }

    /**
     * 触发分片合并、最终 PDF 完整性校验并原子创建提交记录。
     *
     * @param uploadId 上传会话全局 Token，不能为 null
     * @return 创建成功的提交版本视图对象
     */
    @Operation(summary = "完成 PDF 分片上传并创建提交")
    @PostMapping("/uploads/{uploadId}/complete")
    public Result<SubmissionVO> completeUpload(@PathVariable String uploadId) {
        return Result.ok(uploadService.complete(uploadId, UserContext.getUserId()));
    }

    /**
     * 申请人主动取消未完成的分片上传会话并清理暂存碎片。
     *
     * @param uploadId 上传会话全局 Token，不能为 null
     * @return 统一成功空响应
     */
    @Operation(summary = "取消 PDF 分片上传")
    @DeleteMapping("/uploads/{uploadId}")
    public Result<Void> cancelUpload(@PathVariable String uploadId) {
        uploadService.cancel(uploadId, UserContext.getUserId());
        return Result.ok();
    }

    /**
     * 查询指定队伍的全部历史提交记录列表（倒序排列）。
     *
     * @param teamId 目标队伍 ID，不能为 null
     * @return 历史提交记录视图列表
     */
    @Operation(summary = "查询队伍提交历史")
    @GetMapping("/teams/{teamId}")
    public Result<List<SubmissionVO>> history(@PathVariable Long teamId) {
        return Result.ok(submissionService.history(teamId, UserContext.getUserId()));
    }

    /**
     * 实训结束后锁定队伍的最终评审版本并触发榜单与评审派发。
     *
     * @param teamId 目标队伍 ID，不能为 null
     * @return 最终锁定的提交视图对象
     */
    @Operation(summary = "锁定最终提交版本")
    @PostMapping("/teams/{teamId}/finalize")
    public Result<SubmissionVO> finalizeSubmission(@PathVariable Long teamId) {
        return Result.ok(submissionService.lockFinal(teamId, UserContext.getUserId()));
    }
}
