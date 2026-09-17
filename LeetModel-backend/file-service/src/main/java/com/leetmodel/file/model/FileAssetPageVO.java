package com.leetmodel.file.model;

import com.leetmodel.common.core.result.PageResult;

import java.util.List;

public record FileAssetPageVO(
        long totalFiles,
        long totalBytes,
        long manualFiles,
        long discoveredFiles,
        List<FileGroupVO> groups,
        PageResult<FileAssetVO> page
) {
}
