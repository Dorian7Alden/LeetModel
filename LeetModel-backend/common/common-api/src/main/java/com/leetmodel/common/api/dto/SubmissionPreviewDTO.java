package com.leetmodel.common.api.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 管理端按需取得的提交 PDF 临时预览信息。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SubmissionPreviewDTO {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long submissionId;
    private String originalFilename;
    private String previewUrl;
}
