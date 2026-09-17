package com.leetmodel.file.service;

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
}
