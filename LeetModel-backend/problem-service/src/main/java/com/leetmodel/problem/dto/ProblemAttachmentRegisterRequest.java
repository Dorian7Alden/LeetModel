package com.leetmodel.problem.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 绑定已登记文件资产为题目附件的请求。
 *
 * <p>用于客户端预签名直传完成后，把 file-service 返回的 fileId 绑定到题目。</p>
 */
@Data
public class ProblemAttachmentRegisterRequest {

    /** 文件资产 ID，不能为 null */
    @NotNull(message = "文件资产不能为空")
    @Positive(message = "文件资产 ID 必须为正数")
    private Long fileId;

    /** 附件说明 */
    @Size(max = 500, message = "附件说明最多500位")
    private String description;

    /** 展示顺序 */
    @Min(value = 0, message = "展示顺序不能为负数")
    private Integer sortOrder;
}
