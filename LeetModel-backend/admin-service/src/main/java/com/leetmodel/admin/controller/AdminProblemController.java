package com.leetmodel.admin.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.leetmodel.admin.client.ProblemAdminFeignClient;
import com.leetmodel.admin.dto.AdminProblemPageQuery;
import com.leetmodel.admin.service.AdminFeignExecutor;
import com.leetmodel.common.core.result.Result;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/** 管理端题目、标签、赛事与附件统一入口。 */
@Validated
@RestController
@RequestMapping("/api/admin/content")
@RequiredArgsConstructor
@SaCheckRole("admin")
public class AdminProblemController {
    private final ProblemAdminFeignClient problemClient;
    private final AdminFeignExecutor executor;

    @GetMapping("/problems")
    public Result<Object> page(@Valid AdminProblemPageQuery query) {
        return executor.forward("题目服务", () -> problemClient.page(query));
    }

    @GetMapping("/problems/{id}")
    public Result<Object> detail(@PathVariable @Positive Long id) {
        return executor.forward("题目服务", () -> problemClient.detail(id));
    }

    @PostMapping("/problems")
    public Result<Object> create(@RequestBody Map<String, Object> request) {
        return executor.forward("题目服务", () -> problemClient.create(request));
    }

    @PutMapping("/problems/{id}")
    public Result<Object> update(@PathVariable @Positive Long id,
                                 @RequestBody Map<String, Object> request) {
        return executor.forward("题目服务", () -> problemClient.update(id, request));
    }

    @DeleteMapping("/problems/{id}")
    public Result<Void> delete(@PathVariable @Positive Long id) {
        return executor.forward("题目服务", () -> problemClient.delete(id));
    }

    @GetMapping("/tags")
    public Result<Object> listTags() {
        return executor.forward("题目服务", problemClient::listTags);
    }

    @PostMapping("/tags")
    public Result<Object> createTag(@RequestBody Map<String, Object> request) {
        return executor.forward("题目服务", () -> problemClient.createTag(request));
    }

    @PutMapping("/tags/{id}")
    public Result<Object> updateTag(@PathVariable @Positive Long id,
                                    @RequestBody Map<String, Object> request) {
        return executor.forward("题目服务", () -> problemClient.updateTag(id, request));
    }

    @DeleteMapping("/tags/{id}")
    public Result<Void> deleteTag(@PathVariable @Positive Long id) {
        return executor.forward("题目服务", () -> problemClient.deleteTag(id));
    }

    @GetMapping("/contests")
    public Result<Object> listContests() {
        return executor.forward("题目服务", problemClient::listContests);
    }

    @PostMapping(value = "/problems/{id}/attachments", consumes = "multipart/form-data")
    public Result<Object> uploadAttachment(
            @PathVariable @Positive Long id,
            @RequestPart("file") MultipartFile file,
            @RequestParam(required = false) @Size(max = 500) String description,
            @RequestParam(required = false) @Min(0) Integer sortOrder) {
        return executor.forward("题目服务",
                () -> problemClient.uploadAttachment(id, file, description, sortOrder));
    }

    @DeleteMapping("/problems/{problemId}/attachments/{attachmentId}")
    public Result<Void> deleteAttachment(@PathVariable @Positive Long problemId,
                                         @PathVariable @Positive Long attachmentId) {
        return executor.forward("题目服务",
                () -> problemClient.deleteAttachment(problemId, attachmentId));
    }
}
