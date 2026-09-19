package com.leetmodel.file.service;

import com.leetmodel.common.api.dto.FileAssetSummaryDTO;
import com.leetmodel.file.model.FileAccessUrlVO;
import com.leetmodel.file.model.FileAssetPageVO;
import com.leetmodel.file.model.FileAssetQuery;
import com.leetmodel.file.model.FileAssetVO;
import com.leetmodel.file.model.FileReconcileVO;
import org.springframework.web.multipart.MultipartFile;

public interface FileAssetService {
    FileAssetPageVO page(FileAssetQuery query);
    FileAssetVO upload(MultipartFile file, String groupPath, Long creatorId);
    FileAccessUrlVO createAccessUrl(Long id);
    FileAccessUrlVO createPreviewUrl(Long id);
    void requestDelete(Long id);
    FileReconcileVO reconcile();
    int cleanupDueAssets();

    /**
     * 按业务用途登记文件资产，返回可绑定的稳定 fileId 摘要。
     *
     * @param purposeCode 业务用途编码
     * @param groupPath 逻辑分组路径，可为空
     * @param file 待登记文件
     * @param creatorId 发起人标识，可为空
     * @return 文件资产摘要
     */
    FileAssetSummaryDTO registerForPurpose(String purposeCode, String groupPath,
                                           MultipartFile file, Long creatorId);

    /**
     * 按 fileId 查询文件资产摘要。
     *
     * @param fileId 文件资产标识
     * @return 文件资产摘要
     */
    FileAssetSummaryDTO summary(Long fileId);

    /**
     * 按 fileId 生成短时效访问地址。
     *
     * @param fileId 文件资产标识
     * @return 预签名访问地址
     */
    FileAccessUrlVO createAccessUrlByFileId(Long fileId);
}
