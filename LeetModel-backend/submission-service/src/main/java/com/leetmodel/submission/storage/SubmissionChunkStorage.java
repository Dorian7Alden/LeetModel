package com.leetmodel.submission.storage;

import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 论文临时分片存储能力。
 */
public interface SubmissionChunkStorage {

    /**
     * 上传一个临时分片。
     * @param objectName 分片对象路径
     * @param file 分片文件
     */
    void upload(String objectName, MultipartFile file);

    /**
     * 按顺序合并临时分片。
     * @param targetObjectName 最终对象路径
     * @param sourceObjectNames 有序分片对象路径
     */
    void compose(String targetObjectName, List<String> sourceObjectNames);

    /**
     * 删除临时对象。
     * @param objectNames 对象路径列表
     */
    void delete(List<String> objectNames);
}
