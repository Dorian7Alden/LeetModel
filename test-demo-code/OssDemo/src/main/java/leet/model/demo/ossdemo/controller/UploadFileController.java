package leet.model.demo.ossdemo.controller;

import leet.model.demo.ossdemo.utils.AliOssUtil;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/upload")
public class UploadFileController {

    private final AliOssUtil aliOssUtil;

    public UploadFileController(AliOssUtil aliOssUtil) {
        this.aliOssUtil = aliOssUtil;
    }

    @PostMapping("/oss")
    public String upload(MultipartFile file,
                         @RequestParam(value = "virtual_dir", required = false, defaultValue = "") String virtualDir) {
        return aliOssUtil.uploadFile(file, virtualDir);
    }

}
