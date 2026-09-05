package com.leetmodel.review.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.leetmodel.common.core.bean.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * PDF 解析滑窗中间分块持久化实体。
 *
 * <p>按窗口序号 windowIndex 记录双页滑窗的多模态解析切片，支持断点续传与顺序装配。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("paper_parse_chunk_artifact")
public class PaperParseChunkArtifact extends BaseEntity {

    /** 提交记录 ID */
    private Long submissionId;

    /** 解析工作流版本，固定为 PAPER_PARSE_V2 */
    private String workflowVersion;

    /** 滑窗执行序号（从 1 递增） */
    private Integer windowIndex;

    /** 窗口起始物理页码 */
    private Integer startPage;

    /** 窗口结束物理页码 */
    private Integer endPage;

    /** 分块状态：SUCCESS、DEGRADED_OCR、FAILED */
    private String status;

    /** 窗口提取的结构化 JSON 字符串 */
    private String chunkJson;

    /** 执行轮次编号 */
    private Integer attemptNo;

    /** 错误信息 */
    private String errorMessage;
}
