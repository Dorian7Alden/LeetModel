package com.leetmodel.admin.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.stp.StpUtil;
import com.leetmodel.admin.client.FileAdminFeignClient;
import com.leetmodel.admin.service.AdminFeignExecutor;
import com.leetmodel.common.api.dto.FileAssetSummaryDTO;
import com.leetmodel.common.api.dto.FileUploadCreateRequestDTO;
import com.leetmodel.common.api.dto.FileUploadPartUrlDTO;
import com.leetmodel.common.api.dto.FileUploadSessionDTO;
import com.leetmodel.common.api.feign.FileFeignClient;
import com.leetmodel.common.core.result.Result;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Validated
@RestController
@RequestMapping("/api/admin/content/files")
@RequiredArgsConstructor
@SaCheckRole("admin")
public class AdminFileController {
    private final FileAdminFeignClient fileClient;
    private final FileFeignClient fileFeignClient;
    private final AdminFeignExecutor executor;

    @GetMapping
    public Result<Object> page(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) @Size(max = 128) String groupPath,
            @RequestParam(required = false) @Size(max = 100) String keyword,
            @RequestParam(required = false) @Size(max = 24) String lifecycleStatus) {
        return executor.forward("文件服务",
                () -> fileClient.page(page, size, groupPath, keyword, lifecycleStatus));
    }

    @PostMapping(consumes = "multipart/form-data")
    public Result<Object> upload(
            @RequestPart("file") MultipartFile file,
            @RequestParam(required = false) @Size(max = 128) String groupPath) {
        return executor.forward("文件服务", () -> fileClient.upload(file, groupPath));
    }

    @PostMapping("/{id}/access-url")
    public Result<Object> createAccessUrl(@PathVariable @Positive Long id) {
        return executor.forward("文件服务", () -> fileClient.createAccessUrl(id));
    }

    @PostMapping("/{id}/preview-url")
    public Result<Object> createPreviewUrl(@PathVariable @Positive Long id) {
        return executor.forward("文件服务", () -> fileClient.createPreviewUrl(id));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable @Positive Long id) {
        return executor.forward("文件服务", () -> fileClient.delete(id));
    }

    @PostMapping("/reconcile")
    public Result<Object> reconcile() {
        return executor.forward("文件服务", fileClient::reconcile);
    }

    /**
     * 创建大文件预签名分片直传会话。
     *
     * @param request 会话创建请求
     * @return 上传会话状态
     */
    @PostMapping("/uploads")
    public Result<FileUploadSessionDTO> createUploadSession(
            @Valid @RequestBody FileUploadCreateRequestDTO request) {
        FileUploadCreateRequestDTO normalized = new FileUploadCreateRequestDTO(
                request.purpose(), request.groupPath(), request.originalName(), request.contentType(),
                request.fileSize(), request.partSize(),
                request.creatorId() != null ? request.creatorId() : StpUtil.getLoginIdAsLong());
        return executor.forward("文件服务", () -> fileFeignClient.createUploadSession(normalized));
    }

    /**
     * 查询直传会话状态与已上传分片。
     *
     * @param sessionId 上传会话标识
     * @return 上传会话状态
     */
    @GetMapping("/uploads/{sessionId}")
    public Result<FileUploadSessionDTO> uploadSession(@PathVariable String sessionId) {
        return executor.forward("文件服务", () -> fileFeignClient.getUploadSession(sessionId));
    }

    /**
     * 获取分片预签名上传地址。
     *
     * @param sessionId 上传会话标识
     * @param partNumber 分片序号，从 1 开始
     * @return 分片上传地址
     */
    @PostMapping("/uploads/{sessionId}/parts/{partNumber}/url")
    public Result<FileUploadPartUrlDTO> uploadPartUrl(@PathVariable String sessionId,
                                                      @PathVariable Integer partNumber) {
        return executor.forward("文件服务",
                () -> fileFeignClient.createUploadPartUrl(sessionId, partNumber));
    }

    /**
     * 确认直传完成并返回正式文件资产。
     *
     * @param sessionId 上传会话标识
     * @return 文件资产摘要
     */
    @PostMapping("/uploads/{sessionId}/complete")
    public Result<FileAssetSummaryDTO> completeUploadSession(@PathVariable String sessionId) {
        return executor.forward("文件服务", () -> fileFeignClient.completeUploadSession(sessionId));
    }

    /**
     * 取消直传会话并清理分片。
     *
     * @param sessionId 上传会话标识
     * @return 空响应
     */
    @DeleteMapping("/uploads/{sessionId}")
    public Result<Void> abortUploadSession(@PathVariable String sessionId) {
        return executor.forward("文件服务", () -> fileFeignClient.abortUploadSession(sessionId));
    }
}
