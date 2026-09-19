package com.leetmodel.common.api.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 预签名分片直传会话状态。
 *
 * @param sessionId 上传会话标识
 * @param fileId 预创建的文件资产标识
 * @param originalName 原始文件名
 * @param fileSize 文件总字节数
 * @param partSize 服务端约定的分片大小
 * @param partCount 分片总数
 * @param status 会话状态
 * @param uploadedPartNumbers 已成功上传的分片序号
 * @param expireTime 会话过期时间
 */
public record FileUploadSessionDTO(
        String sessionId,
        @JsonSerialize(using = ToStringSerializer.class) Long fileId,
        String originalName,
        Long fileSize,
        Long partSize,
        Integer partCount,
        String status,
        List<Integer> uploadedPartNumbers,
        LocalDateTime expireTime
) {
}
