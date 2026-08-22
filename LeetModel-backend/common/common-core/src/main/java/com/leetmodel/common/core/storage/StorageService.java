package com.leetmodel.common.core.storage;

import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

/**
 * 对象存储服务接口 —— 统一抽象，业务代码不依赖具体存储实现。
 *
 * <p>当前实现：MinIO，通过 {@link com.leetmodel.common.core.storage.impl.MinioStorageServiceImpl}。
 * 后续如需切换为 OSS / S3 / 本地文件系统，只需替换实现类，业务代码无需改动。</p>
 *
 * @author LeetModel
 */
public interface StorageService {

    /**
     * 上传文件。
     *
     * @param file 上传的文件（MultipartFile）
     * @return objectName（对象存储中的唯一标识，UUID + 扩展名格式）
     */
    String upload(MultipartFile file);

    /**
     * 上传文件到指定路径前缀。
     *
     * @param file   上传的文件
     * @param prefix 存储路径前缀，如 "avatars"、"problems"
     * @return objectName
     */
    String upload(MultipartFile file, String prefix);

    /**
     * 下载文件。
     *
     * @param objectName 对象名称
     * @return 文件输入流
     */
    InputStream download(String objectName);

    /**
     * 获取文件访问 URL（预签名 URL）。
     * 前端可直接通过此 URL 访问文件，不经过业务服务。
     *
     * @param objectName 对象名称
     * @return 预签名访问 URL
     */
    String getUrl(String objectName);

    /**
     * 删除文件。
     *
     * @param objectName 对象名称
     */
    void delete(String objectName);
}
