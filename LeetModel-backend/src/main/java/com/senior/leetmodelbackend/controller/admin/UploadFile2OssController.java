package com.senior.leetmodelbackend.controller.admin;

import com.senior.leetmodelbackend.utils.OssUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/admin")
public class UploadFile2OssController {

    private final OssUtils ossUtils;
    public UploadFile2OssController(OssUtils ossUtils) {
        this.ossUtils = ossUtils;
    }

    @PostMapping("/upload")
    public String uploadFile2Oss(MultipartFile file) {
        return ossUtils.uploadFile(file);
    }

}
