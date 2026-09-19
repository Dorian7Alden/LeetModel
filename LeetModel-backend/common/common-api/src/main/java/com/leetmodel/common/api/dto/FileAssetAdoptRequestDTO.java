package com.leetmodel.common.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * 既有对象接管请求。
 *
 * <p>用于业务服务在自身受控的交接目录中生成对象后，交由 file-service 接管技术元数据与生命周期。
 * 调用方只能提交用途、交接目录对象路径与文件元信息，物理路由不会成为业务长期事实。</p>
 *
 * @param purpose 业务用途编码
 * @param objectKey 交接目录中的对象路径
 * @param originalName 原始文件名
 * @param contentType 媒体类型
 * @param fileSize 声明文件字节数，由 file-service 核对实际对象
 * @param groupPath 逻辑分组路径，可为空
 * @param creatorId 发起人标识，可为空
 */
public record FileAssetAdoptRequestDTO(
        @NotBlank String purpose,
        @NotBlank String objectKey,
        @NotBlank String originalName,
        @NotBlank String contentType,
        @NotNull @Positive Long fileSize,
        String groupPath,
        Long creatorId
) {
}
