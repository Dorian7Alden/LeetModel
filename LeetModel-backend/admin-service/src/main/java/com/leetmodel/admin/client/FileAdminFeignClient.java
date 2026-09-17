package com.leetmodel.admin.client;

import com.leetmodel.common.core.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

@FeignClient(name = "file-service", contextId = "fileAdminFeignClient")
public interface FileAdminFeignClient {
    @org.springframework.web.bind.annotation.GetMapping("/api/file-assets")
    Result<Object> page(@RequestParam("page") int page,
                        @RequestParam("size") int size,
                        @RequestParam(value = "groupPath", required = false) String groupPath,
                        @RequestParam(value = "keyword", required = false) String keyword,
                        @RequestParam(value = "lifecycleStatus", required = false) String lifecycleStatus);

    @PostMapping(value = "/api/file-assets", consumes = "multipart/form-data")
    Result<Object> upload(@RequestPart("file") MultipartFile file,
                          @RequestParam(value = "groupPath", required = false) String groupPath);

    @PostMapping("/api/file-assets/{id}/access-url")
    Result<Object> createAccessUrl(@PathVariable("id") Long id);

    @PostMapping("/api/file-assets/{id}/preview-url")
    Result<Object> createPreviewUrl(@PathVariable("id") Long id);

    @DeleteMapping("/api/file-assets/{id}")
    Result<Void> delete(@PathVariable("id") Long id);

    @PostMapping("/api/file-assets/reconcile")
    Result<Object> reconcile();
}
