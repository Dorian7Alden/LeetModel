package com.leetmodel.file.service;

import com.leetmodel.common.api.dto.FileAssetSummaryDTO;
import com.leetmodel.common.api.dto.FileUploadCreateRequestDTO;
import com.leetmodel.common.api.dto.FileUploadPartUrlDTO;
import com.leetmodel.common.api.dto.FileUploadSessionDTO;

/**
 * 预签名分片直传会话服务。
 *
 * <p>客户端按会话约定的分片大小直传对象存储，二进制不经过业务服务与 file-service 中转。</p>
 */
public interface FileUploadService {

    /**
     * 创建直传会话并预创建文件资产。
     *
     * @param request 会话创建请求
     * @return 上传会话状态
     */
    FileUploadSessionDTO create(FileUploadCreateRequestDTO request);

    /**
     * 查询上传会话状态与已上传分片。
     *
     * @param sessionId 上传会话标识
     * @return 上传会话状态
     */
    FileUploadSessionDTO status(String sessionId);

    /**
     * 获取指定分片的预签名上传地址。
     *
     * @param sessionId 上传会话标识
     * @param partNumber 分片序号，从 1 开始
     * @return 分片上传地址
     */
    FileUploadPartUrlDTO partUrl(String sessionId, Integer partNumber);

    /**
     * 确认分片全部上传完成并合并为正式文件。
     *
     * @param sessionId 上传会话标识
     * @return 文件资产摘要
     */
    FileAssetSummaryDTO complete(String sessionId);

    /**
     * 取消上传会话并清理已上传分片。
     *
     * @param sessionId 上传会话标识
     */
    void abort(String sessionId);

    /**
     * 清理过期上传会话与其分片对象。
     *
     * @return 处理的会话数量
     */
    int cleanupExpiredSessions();
}
