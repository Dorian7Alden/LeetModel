package com.leetmodel.review.parse.v2;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * PDF 解析 V2 核心配置属性。
 *
 * <p>控制逐页离散化渲染分辨率、压缩质量、重试与退避参数及模型配置版本。</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "review.parse.v2")
public class PaperParseV2Properties {

    /** 支持解析的最大物理页数 */
    private int maxPages = 80;

    /** 逐页渲染 DPI，默认 130 兼顾高清空间感知与网络传输体积 */
    private int renderDpi = 130;

    /** JPEG 编码压缩质量（0.0 到 1.0） */
    private float jpegQuality = 0.82f;

    /** 单页 JPEG 最大字节数上限（默认 8MB） */
    private long maxImageBytes = 8 * 1024 * 1024L;

    /** 单个滑窗多模态请求最大重试次数 */
    private int maxRetries = 2;

    /** 局部重试初始退避延迟毫秒 */
    private long retryDelayMs = 1000L;

    /** 视觉多模态大模型执行配置版本 */
    private String visionModelConfigVersion = "MODEL_CFG_PAPER_PARSE_MULTIMODAL_0001";

    /** 重叠冲突仲裁文本模型执行配置版本 */
    private String arbiterModelConfigVersion = "MODEL_CFG_PAPER_PARSE_TEXT_0001";
}
