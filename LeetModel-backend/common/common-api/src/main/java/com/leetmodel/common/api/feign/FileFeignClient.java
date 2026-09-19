package com.leetmodel.common.api.feign;

import com.leetmodel.common.api.dto.FileAccessUrlDTO;
import com.leetmodel.common.api.dto.FileAssetAdoptRequestDTO;
import com.leetmodel.common.api.dto.FileAssetSummaryDTO;
import com.leetmodel.common.api.dto.FileUploadCreateRequestDTO;
import com.leetmodel.common.api.dto.FileUploadPartUrlDTO;
import com.leetmodel.common.api.dto.FileUploadSessionDTO;
import com.leetmodel.common.core.result.Result;
import jakarta.validation.Valid;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件服务内部 Feign 客户端。
 *
 * <p>业务服务只声明用途与逻辑分组，物理对象命名、访问级别和生命周期由 file-service 决定。</p>
 */
@FeignClient(name = "file-service", contextId = "fileFeignClient")
public interface FileFeignClient {

    /**
     * 按业务用途登记文件资产。
     *
     * @param purpose 业务用途编码
     * @param groupPath 逻辑分组路径，可为空
     * @param creatorId 发起人标识，可为空
     * @param file 待登记文件
     * @return 文件资产摘要
     */
    @PostMapping(value = "/internal/file-assets", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    Result<FileAssetSummaryDTO> registerForPurpose(
            @RequestParam("purpose") String purpose,
            @RequestParam(value = "groupPath", required = false) String groupPath,
            @RequestParam(value = "creatorId", required = false) Long creatorId,
            @RequestPart("file") MultipartFile file
    );

    /**
     * 查询文件资产摘要。
     *
     * @param fileId 文件资产标识
     * @return 文件资产摘要
     */
    @GetMapping("/internal/file-assets/{fileId}")
    Result<FileAssetSummaryDTO> getSummary(@PathVariable("fileId") Long fileId);

    /**
     * 接管业务服务在交接目录中已生成的对象。
     *
     * @param request 接管请求
     * @return 文件资产摘要
     */
    @PostMapping("/internal/file-assets/adopt")
    Result<FileAssetSummaryDTO> adopt(@RequestBody FileAssetAdoptRequestDTO request);

    /**
     * 创建预签名分片直传会话。
     *
     * @param request 会话创建请求
     * @return 上传会话状态
     */
    @PostMapping("/internal/file-uploads")
    Result<FileUploadSessionDTO> createUploadSession(@Valid @RequestBody FileUploadCreateRequestDTO request);

    /**
     * 查询预签名直传会话状态。
     *
     * @param sessionId 上传会话标识
     * @return 上传会话状态
     */
    @GetMapping("/internal/file-uploads/{sessionId}")
    Result<FileUploadSessionDTO> getUploadSession(@PathVariable("sessionId") String sessionId);

    /**
     * 获取指定分片的预签名上传地址。
     *
     * @param sessionId 上传会话标识
     * @param partNumber 分片序号，从 1 开始
     * @return 分片上传地址
     */
    @PostMapping("/internal/file-uploads/{sessionId}/parts/{partNumber}/url")
    Result<FileUploadPartUrlDTO> createUploadPartUrl(
            @PathVariable("sessionId") String sessionId,
            @PathVariable("partNumber") Integer partNumber);

    /**
     * 确认分片全部上传完成并合并为正式文件。
     *
     * @param sessionId 上传会话标识
     * @return 文件资产摘要
     */
    @PostMapping("/internal/file-uploads/{sessionId}/complete")
    Result<FileAssetSummaryDTO> completeUploadSession(@PathVariable("sessionId") String sessionId);

    /**
     * 取消上传会话并清理已上传分片。
     *
     * @param sessionId 上传会话标识
     * @return 空响应
     */
    @DeleteMapping("/internal/file-uploads/{sessionId}")
    Result<Void> abortUploadSession(@PathVariable("sessionId") String sessionId);

    /**
     * 生成文件短时效访问地址。
     *
     * @param fileId 文件资产标识
     * @return 预签名访问地址与有效秒数
     */
    @PostMapping("/internal/file-assets/{fileId}/access-url")
    Result<FileAccessUrlDTO> createAccessUrl(@PathVariable("fileId") Long fileId);
}
