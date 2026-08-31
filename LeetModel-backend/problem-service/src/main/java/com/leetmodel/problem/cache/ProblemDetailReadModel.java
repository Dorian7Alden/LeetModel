package com.leetmodel.problem.cache;

import com.leetmodel.problem.vo.ProblemVO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 不包含 MinIO 预签名 URL 的已发布题目详情读模型。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProblemDetailReadModel {

    private ProblemVO problem;
    private List<AttachmentReadModel> attachments;

    /**
     * 可缓存的附件元数据。
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AttachmentReadModel {
        private Long id;
        private String fileName;
        private String objectKey;
        private String contentType;
        private Long fileSize;
        private String description;
        private Integer sortOrder;
    }
}
