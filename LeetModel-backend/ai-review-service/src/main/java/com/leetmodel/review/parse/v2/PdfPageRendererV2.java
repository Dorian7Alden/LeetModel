package com.leetmodel.review.parse.v2;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.stereotype.Component;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Iterator;

/**
 * 第二代 PDF 逐页高保真图像离散化渲染器。
 *
 * <p>按需对单物理页执行指定 DPI 的光栅化渲染并压缩为 JPEG Base64 Data URL，
 * 遵循内存友好原则，避免全量文档高清位图常驻堆内存。</p>
 */
@Component
public class PdfPageRendererV2 {

    private final PaperParseV2Properties properties;

    public PdfPageRendererV2(PaperParseV2Properties properties) {
        this.properties = properties;
    }

    /**
     * 将指定物理页渲染为 Base64 JPEG Data URL。
     *
     * @param document  已加载的 PDF 文档对象
     * @param pageIndex 0 索引页码（0 对应第 1 页）
     * @return 符合 RFC 2397 规范的 data:image/jpeg;base64,... 字符串
     * @throws IOException 渲染或 IO 异常
     */
    public String renderPageDataUrl(PDDocument document, int pageIndex) throws IOException {
        PDFRenderer renderer = new PDFRenderer(document);
        BufferedImage image = renderer.renderImageWithDPI(
                pageIndex,
                properties.getRenderDpi(),
                ImageType.RGB
        );
        byte[] jpegBytes = encodeJpeg(image);
        if (jpegBytes.length > properties.getMaxImageBytes()) {
            throw new IllegalArgumentException("第 " + (pageIndex + 1) + " 页图像体积超限: " + jpegBytes.length);
        }
        return "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(jpegBytes);
    }

    private byte[] encodeJpeg(BufferedImage image) throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) {
            throw new IllegalStateException("当前 JVM 运行环境缺少 JPEG 编码器");
        }
        ImageWriter writer = writers.next();
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
             ImageOutputStream imageOutput = ImageIO.createImageOutputStream(output)) {
            writer.setOutput(imageOutput);
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(properties.getJpegQuality());
            writer.write(null, new IIOImage(image, null, null), param);
            return output.toByteArray();
        } finally {
            writer.dispose();
        }
    }
}
