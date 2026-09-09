package com.leetmodel.admin.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.leetmodel.admin.client.FileAdminFeignClient;
import com.leetmodel.admin.service.AdminFeignExecutor;
import com.leetmodel.common.core.result.Result;
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
}
