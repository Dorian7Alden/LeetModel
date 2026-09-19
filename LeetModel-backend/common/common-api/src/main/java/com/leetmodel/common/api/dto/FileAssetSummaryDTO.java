package com.leetmodel.common.api.dto;

/**
 * 文件资产最小摘要。
 *
 * <p>供业务服务在保存稳定 fileId 后按需查询展示信息，不暴露对象存储物理路由。</p>
 *
 * @param fileId 文件资产稳定标识
 * @param namespaceCode 文件用途命名空间
 * @param sourceType 文件来源类型
 * @param originalName 原始文件名
 * @param contentType 媒体类型
 * @param fileSize 文件字节数
 * @param lifecycleStatus 文件资产生命周期状态
 */
public record FileAssetSummaryDTO(
        Long fileId,
        String namespaceCode,
        String sourceType,
        String originalName,
        String contentType,
        Long fileSize,
        String lifecycleStatus
) {
}
