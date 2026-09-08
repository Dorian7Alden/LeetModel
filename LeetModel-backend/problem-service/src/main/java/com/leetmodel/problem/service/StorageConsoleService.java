package com.leetmodel.problem.service;

import com.leetmodel.problem.vo.StorageObjectVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 存储桶对象资产控制台服务接口。
 */
public interface StorageConsoleService {

    /**
     * 扫描存储桶对象列表，并与数据库业务引用进行对账。
     *
     * @param keyword      搜索关键词（匹配 objectKey 或关联题目）
     * @param onlyOrphans  是否只筛选孤儿文件（未关联任何业务的文件）
     * @return 对象资产视图列表
     */
    List<StorageObjectVO> listObjects(String keyword, Boolean onlyOrphans);

    /**
     * 管理员手动上传文件到指定存储前缀。
     *
     * @param file   上传文件
     * @param prefix 目标前缀（如 manual、problems）
     * @return 上传后的对象视图
     */
    StorageObjectVO uploadObject(MultipartFile file, String prefix);

    /**
     * 安全删除对象存储文件：严格防误删校验。
     * 若文件仍被题目附件引用，强行阻断并提示关联题目；仅孤儿文件允许物理清除。
     *
     * @param objectKey 对象路径
     */
    void deleteObjectSafely(String objectKey);
}
