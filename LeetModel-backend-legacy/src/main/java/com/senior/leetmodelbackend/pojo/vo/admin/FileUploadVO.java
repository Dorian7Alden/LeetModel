package com.senior.leetmodelbackend.pojo.vo.admin;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class FileUploadVO {
    private Integer fileId;
    private String fileUrl;
}
