package com.leetmodel.file.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FileCleanupScheduler {
    private final FileAssetService fileAssetService;

    @Scheduled(fixedDelayString = "${file.lifecycle.cleanup-interval-ms:60000}")
    public void cleanup() {
        fileAssetService.cleanupDueAssets();
    }
}
