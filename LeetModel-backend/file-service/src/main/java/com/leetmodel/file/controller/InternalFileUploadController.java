package com.leetmodel.file.controller;

import com.leetmodel.common.api.dto.FileAssetSummaryDTO;
import com.leetmodel.common.api.dto.FileUploadCreateRequestDTO;
import com.leetmodel.common.api.dto.FileUploadPartUrlDTO;
import com.leetmodel.common.api.dto.FileUploadSessionDTO;
import com.leetmodel.common.core.result.Result;
import com.leetmodel.file.service.FileUploadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 预签名分片直传内部接口。
 *
 * <p>仅供服务间调用；客户端只拿到分片预签名上传地址，字节直接写入对象存储。</p>
 */
@Tag(name = "内部接口-文件直传")
@RestController
@RequestMapping("/internal/file-uploads")
@RequiredArgsConstructor
public class InternalFileUploadController {

    private final FileUploadService fileUploadService;

    /**
     * 创建直传会话。
     *
     * @param request 会话创建请求
     * @return 上传会话状态
     */
    @Operation(summary = "创建直传会话")
    @PostMapping
    public Result<FileUploadSessionDTO> create(@Valid @RequestBody FileUploadCreateRequestDTO request) {
        return Result.ok(fileUploadService.create(request));
    }

    /**
     * 查询直传会话状态。
     *
     * @param sessionId 上传会话标识
     * @return 上传会话状态
     */
    @Operation(summary = "查询直传会话状态")
    @GetMapping("/{sessionId}")
    public Result<FileUploadSessionDTO> status(@PathVariable String sessionId) {
        return Result.ok(fileUploadService.status(sessionId));
    }

    /**
     * 获取分片预签名上传地址。
     *
     * @param sessionId 上传会话标识
     * @param partNumber 分片序号，从 1 开始
     * @return 分片上传地址
     */
    @Operation(summary = "获取分片上传地址")
    @PostMapping("/{sessionId}/parts/{partNumber}/url")
    public Result<FileUploadPartUrlDTO> partUrl(@PathVariable String sessionId,
                                                @PathVariable Integer partNumber) {
        return Result.ok(fileUploadService.partUrl(sessionId, partNumber));
    }

    /**
     * 确认分片上传完成并合并。
     *
     * @param sessionId 上传会话标识
     * @return 文件资产摘要
     */
    @Operation(summary = "确认直传完成")
    @PostMapping("/{sessionId}/complete")
    public Result<FileAssetSummaryDTO> complete(@PathVariable String sessionId) {
        return Result.ok(fileUploadService.complete(sessionId));
    }

    /**
     * 取消直传会话。
     *
     * @param sessionId 上传会话标识
     * @return 空响应
     */
    @Operation(summary = "取消直传会话")
    @DeleteMapping("/{sessionId}")
    public Result<Void> abort(@PathVariable String sessionId) {
        fileUploadService.abort(sessionId);
        return Result.ok();
    }
}
