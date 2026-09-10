package com.leetmodel.problem.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.leetmodel.common.core.result.Result;
import com.leetmodel.problem.service.StorageConsoleService;
import com.leetmodel.problem.vo.StorageObjectVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 管理端存储桶对象资产治理控制器。
 */
@RestController
@RequestMapping("/api/storage")
@RequiredArgsConstructor
@SaCheckRole("admin")
@Tag(name = "存储对象资产治理")
public class StorageConsoleController {

    private final StorageConsoleService storageConsoleService;

    @Operation(summary = "扫描存储桶对象列表并识别题目附件引用")
    @GetMapping("/objects")
    public Result<List<StorageObjectVO>> listObjects(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean onlyOrphans
    ) {
        return Result.ok(storageConsoleService.listObjects(keyword, onlyOrphans));
    }

    @Operation(summary = "手动上传文件至存储桶")
    @PostMapping(path = "/objects", consumes = "multipart/form-data")
    public Result<StorageObjectVO> uploadObject(
            @RequestPart("file") MultipartFile file,
            @RequestParam(required = false, defaultValue = "manual") String prefix
    ) {
        return Result.ok(storageConsoleService.uploadObject(file, prefix));
    }

    @Operation(summary = "安全删除存储桶对象（防误删校验）")
    @DeleteMapping("/objects")
    public Result<Void> deleteObject(@RequestParam String objectKey) {
        storageConsoleService.deleteObjectSafely(objectKey);
        return Result.ok();
    }
}
