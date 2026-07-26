package com.senior.leetmodelbackend.pojo.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class OssFile {

    private Integer fileId;
    private String fileName;
    private String fileUrl;
    private String fileSuffix;
    private String contentType;
    private Long fileSize;
    private Integer uploaderId;
    private Boolean isDeleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

}
