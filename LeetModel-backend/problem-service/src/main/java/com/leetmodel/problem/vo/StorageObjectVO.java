package com.leetmodel.problem.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 存储桶对象资产视图对象（含业务引用与孤儿状态标记）。
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

    /** 引用计数：0 表示孤儿文件（未关联），>=1 表示使用中 */
    private Integer refCount;

    /** 关联题目 ID（若存在） */
    private Long refProblemId;

    /** 关联题目标题（若存在） */
    private String refProblemTitle;

    /** 预签名下载直链（有界时效） */
    private String downloadUrl;
}
