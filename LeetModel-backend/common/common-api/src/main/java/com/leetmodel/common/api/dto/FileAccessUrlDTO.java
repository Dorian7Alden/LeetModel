package com.leetmodel.common.api.dto;

/**
 * 文件有界时效访问地址。
 *
 * @param url 预签名访问地址
 * @param expirySeconds 地址有效秒数
 */
public record FileAccessUrlDTO(String url, int expirySeconds) {
}
