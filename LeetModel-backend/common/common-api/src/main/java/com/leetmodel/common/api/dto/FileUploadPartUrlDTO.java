package com.leetmodel.common.api.dto;

/**
 * 单个分片的预签名上传地址。
 *
 * @param partNumber 分片序号，从 1 开始
 * @param uploadUrl 预签名 PUT 上传地址
 * @param urlExpirySeconds 地址有效秒数
 */
public record FileUploadPartUrlDTO(
        Integer partNumber,
        String uploadUrl,
        int urlExpirySeconds
) {
}
