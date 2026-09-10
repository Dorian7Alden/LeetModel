package com.leetmodel.problem.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 存储桶对象资产视图对象，包含当前服务能够识别的题目附件引用。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StorageObjectVO {

    /** 对象存储完整路径 Key */
    private String objectKey;

    /** 文件名（推断或提取名称） */
    private String fileName;

    /** 文件字节数 */
    private Long fileSize;

    /** 最后修改时间 */
    private LocalDateTime lastModified;

    /** 已识别的题目附件引用计数：0 不代表其他业务一定没有引用 */
    private Integer refCount;

    /** 关联题目 ID（若存在） */
    private Long refProblemId;

    /** 关联题目标题（若存在） */
    private String refProblemTitle;

    /** 预签名下载直链（有界时效） */
    private String downloadUrl;
}
