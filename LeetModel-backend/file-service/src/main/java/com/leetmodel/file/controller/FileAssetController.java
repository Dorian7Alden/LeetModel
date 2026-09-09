package com.leetmodel.file.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.leetmodel.common.core.result.Result;
import com.leetmodel.common.security.context.UserContext;
import com.leetmodel.file.model.FileAccessUrlVO;
import com.leetmodel.file.model.FileAssetPageVO;
import com.leetmodel.file.model.FileAssetQuery;
import com.leetmodel.file.model.FileAssetVO;
import com.leetmodel.file.model.FileReconcileVO;
import com.leetmodel.file.service.FileAssetService;
import jakarta.validation.Valid;
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
@RequestMapping("/api/file-assets")
@RequiredArgsConstructor
@SaCheckRole("admin")
public class FileAssetController {
    private final FileAssetService fileAssetService;

    @GetMapping
    public Result<FileAssetPageVO> page(@Valid FileAssetQuery query) {
        return Result.ok(fileAssetService.page(query));
    }

    @PostMapping(consumes = "multipart/form-data")
    public Result<FileAssetVO> upload(
            @RequestPart("file") MultipartFile file,
            @RequestParam(required = false) @Size(max = 128) String groupPath) {
        return Result.ok(fileAssetService.upload(file, groupPath, UserContext.getUserId()));
    }

    @PostMapping("/{id}/access-url")
    public Result<FileAccessUrlVO> createAccessUrl(@PathVariable @Positive Long id) {
        return Result.ok(fileAssetService.createAccessUrl(id));
    }

    @PostMapping("/{id}/preview-url")
    public Result<FileAccessUrlVO> createPreviewUrl(@PathVariable @Positive Long id) {
        return Result.ok(fileAssetService.createPreviewUrl(id));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable @Positive Long id) {
        fileAssetService.requestDelete(id);
        return Result.ok();
    }

    @PostMapping("/reconcile")
    public Result<FileReconcileVO> reconcile() {
        return Result.ok(fileAssetService.reconcile());
    }
}
