package com.leetmodel.submission.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 初始化论文分片上传请求。
 */
@Data
public class UploadInitializeRequest {

    @NotNull(message = "队伍不能为空")
    @Positive(message = "队伍 ID 必须为正数")
    private Long teamId;

    @NotBlank(message = "文件名不能为空")
    @Size(max = 255, message = "文件名最多255位")
    private String originalFilename;

    @NotNull(message = "文件大小不能为空")
    @Positive(message = "文件大小必须为正数")
    private Long fileSize;

    @NotBlank(message = "文件摘要不能为空")
    @Pattern(regexp = "^[0-9a-fA-F]{64}$", message = "文件摘要格式不正确")
    private String fileSha256;
}
