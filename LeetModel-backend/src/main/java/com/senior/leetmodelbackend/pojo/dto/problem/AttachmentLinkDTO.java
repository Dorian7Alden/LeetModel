package com.senior.leetmodelbackend.pojo.dto.problem;

import lombok.Data;

/**
 * 附件链接
 */
@Data
public class AttachmentLinkDTO {

    private String linkTitle;
    private String linkUrl;

    /**
     * 展示优先级（越小越靠前）
     */
    private Integer displayPriority = 0;
}
