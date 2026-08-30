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

    @Operation(summary = "初始化或恢复 PDF 分片上传")
    @PostMapping("/uploads")
    public Result<UploadSessionVO> initializeUpload(
            @Valid @RequestBody UploadInitializeRequest request
    ) {
        return Result.ok(uploadService.initialize(request, UserContext.getUserId()));
    }

    @Operation(summary = "查询 PDF 分片上传状态")
    @GetMapping("/uploads/{uploadId}")
    public Result<UploadSessionVO> getUpload(@PathVariable String uploadId) {
        return Result.ok(uploadService.get(uploadId, UserContext.getUserId()));
    }

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

    @Operation(summary = "完成 PDF 分片上传并创建提交")
    @PostMapping("/uploads/{uploadId}/complete")
    public Result<SubmissionVO> completeUpload(@PathVariable String uploadId) {
        return Result.ok(uploadService.complete(uploadId, UserContext.getUserId()));
    }

    @Operation(summary = "取消 PDF 分片上传")
    @DeleteMapping("/uploads/{uploadId}")
    public Result<Void> cancelUpload(@PathVariable String uploadId) {
        uploadService.cancel(uploadId, UserContext.getUserId());
        return Result.ok();
    }

    @Operation(summary = "查询队伍提交历史")
    @GetMapping("/teams/{teamId}")
    public Result<List<SubmissionVO>> history(@PathVariable Long teamId) {
        return Result.ok(submissionService.history(teamId, UserContext.getUserId()));
    }

    @Operation(summary = "锁定最终提交版本")
    @PostMapping("/teams/{teamId}/finalize")
    public Result<SubmissionVO> finalizeSubmission(@PathVariable Long teamId) {
        return Result.ok(submissionService.lockFinal(teamId, UserContext.getUserId()));
    }
}
