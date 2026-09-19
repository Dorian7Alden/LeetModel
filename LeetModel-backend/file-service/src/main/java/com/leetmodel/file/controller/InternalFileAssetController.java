package com.leetmodel.file.controller;

import com.leetmodel.common.api.dto.FileAccessUrlDTO;
import com.leetmodel.common.api.dto.FileAssetAdoptRequestDTO;
import com.leetmodel.common.api.dto.FileAssetSummaryDTO;
import com.leetmodel.common.core.result.Result;
import com.leetmodel.file.model.FileAccessUrlVO;
import com.leetmodel.file.service.FileAssetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件服务内部接口。
 *
 * <p>仅供服务间调用；网关不路由 /internal 前缀，业务服务只能声明用途与逻辑分组。</p>
 */
@Tag(name = "内部接口")
@RestController
@RequestMapping("/internal/file-assets")
@RequiredArgsConstructor
public class InternalFileAssetController {

    private final FileAssetService fileAssetService;

    /**
     * 按业务用途登记文件资产。
     *
     * @param purpose 业务用途编码
     * @param groupPath 逻辑分组路径，可为空
     * @param creatorId 发起人标识，可为空
     * @param file 待登记文件
     * @return 文件资产摘要
     */
    @Operation(summary = "登记业务文件资产")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<FileAssetSummaryDTO> registerForPurpose(
            @RequestParam("purpose") String purpose,
            @RequestParam(value = "groupPath", required = false) String groupPath,
            @RequestParam(value = "creatorId", required = false) Long creatorId,
            @RequestPart("file") MultipartFile file
    ) {
        return Result.ok(fileAssetService.registerForPurpose(purpose, groupPath, file, creatorId));
    }

    /**
     * 查询文件资产摘要。
     *
     * @param fileId 文件资产标识
     * @return 文件资产摘要
     */
    @Operation(summary = "查询文件资产摘要")
    @GetMapping("/{fileId}")
    public Result<FileAssetSummaryDTO> summary(@PathVariable Long fileId) {
        return Result.ok(fileAssetService.summary(fileId));
    }

    /**
     * 生成文件短时效访问地址。
     *
     * @param fileId 文件资产标识
     * @return 预签名访问地址与有效秒数
     */
    @Operation(summary = "生成文件访问地址")
    @PostMapping("/{fileId}/access-url")
    public Result<FileAccessUrlDTO> accessUrl(@PathVariable Long fileId) {
        FileAccessUrlVO access = fileAssetService.createAccessUrlByFileId(fileId);
        return Result.ok(new FileAccessUrlDTO(access.url(), access.expiresInSeconds()));
    }

    /**
     * 接管业务服务在交接目录中已生成的对象。
     *
     * @param request 接管请求
     * @return 文件资产摘要
     */
    @Operation(summary = "接管既有对象")
    @PostMapping("/adopt")
    public Result<FileAssetSummaryDTO> adopt(@Valid @RequestBody FileAssetAdoptRequestDTO request) {
        return Result.ok(fileAssetService.adoptForPurpose(request));
    }
}
