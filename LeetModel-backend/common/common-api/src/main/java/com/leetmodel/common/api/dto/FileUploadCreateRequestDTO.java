package com.leetmodel.common.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * 预签名分片直传会话创建请求。
 *
 * @param purpose 业务用途编码
 * @param groupPath 逻辑分组路径，可为空
 * @param originalName 原始文件名
 * @param contentType 媒体类型
 * @param fileSize 文件总字节数
 * @param partSize 期望分片大小，可为空表示使用服务端默认值
 * @param creatorId 发起人标识，可为空
 */
public record FileUploadCreateRequestDTO(
        @NotBlank String purpose,
        String groupPath,
        @NotBlank String originalName,
        @NotBlank String contentType,
        @NotNull @Positive Long fileSize,
        Long partSize,
        Long creatorId
) {
}
