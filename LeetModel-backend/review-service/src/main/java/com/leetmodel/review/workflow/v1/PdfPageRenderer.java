package com.leetmodel.review.workflow.v1;

import org.apache.pdfbox.Loader;
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
import java.util.ArrayList;
import java.util.Base64;
import java.util.Iterator;
import java.util.List;

@Component
public class PdfPageRenderer {
    private final BasicReviewV1Properties properties;

    public PdfPageRenderer(BasicReviewV1Properties properties) {
        this.properties = properties;
    }

    public RenderedPaper render(byte[] pdfBytes) throws Exception {
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            int pageCount = document.getNumberOfPages();
            if (pageCount < 1 || pageCount > properties.getMaxPages()) {
                throw new IllegalArgumentException("V1 支持 1 到 " + properties.getMaxPages() + " 页 PDF");
            }
            PDFRenderer renderer = new PDFRenderer(document);
            List<String> dataUrls = new ArrayList<>(pageCount);
            long totalBytes = 0;
            for (int index = 0; index < pageCount; index++) {
                BufferedImage image = renderer.renderImageWithDPI(index, properties.getRenderDpi(), ImageType.RGB);
                byte[] jpeg = encodeJpeg(image);
                if (jpeg.length > properties.getMaxImageBytes()) {
                    throw new IllegalArgumentException("第 " + (index + 1) + " 页图像超过 V1 大小限制");
                }
                totalBytes += jpeg.length;
                if (totalBytes > properties.getMaxTotalImageBytes()) {
                    throw new IllegalArgumentException("PDF 页面图像总体积超过 V1 大小限制");
                }
                dataUrls.add("data:image/jpeg;base64," + Base64.getEncoder().encodeToString(jpeg));
            }
            return new RenderedPaper(pageCount, totalBytes, List.copyOf(dataUrls));
        }
    }

    private byte[] encodeJpeg(BufferedImage image) throws Exception {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) throw new IllegalStateException("当前运行环境缺少 JPEG 编码器");
        ImageWriter writer = writers.next();
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
             ImageOutputStream imageOutput = ImageIO.createImageOutputStream(output)) {
            writer.setOutput(imageOutput);
            ImageWriteParam parameter = writer.getDefaultWriteParam();
            parameter.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            parameter.setCompressionQuality(properties.getJpegQuality());
            writer.write(null, new IIOImage(image, null, null), parameter);
            return output.toByteArray();
        } finally {
            writer.dispose();
        }
    }

    public record RenderedPaper(int pageCount, long totalBytes, List<String> pageDataUrls) {
    }
}
