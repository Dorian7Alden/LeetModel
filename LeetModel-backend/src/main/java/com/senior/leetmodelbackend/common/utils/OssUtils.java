package com.senior.leetmodelbackend.common.utils;

import com.aliyun.oss.ClientBuilderConfiguration;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.common.auth.DefaultCredentialProvider;
import com.aliyun.oss.common.comm.SignVersion;
import com.senior.leetmodelbackend.config.OssConfig;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

@Slf4j
@Component
public class OssUtils {

    /**
     * 阿里云 OSS 配置
     */
    private final OssConfig ossConfig;
    /**
     * 阿里云 OSS 客户端
     */
    private OSS ossClient;

    public OssUtils(OssConfig ossConfig) {
        this.ossConfig = ossConfig;
    }

    /**
     * 初始化 阿里云 OSS 客户端
     */
    @PostConstruct
    public void init() {
        // 创建凭证提供者
        DefaultCredentialProvider provider = new DefaultCredentialProvider(ossConfig.getAccessKeyId(), ossConfig.getAccessKeySecret());
        ClientBuilderConfiguration clientBuilderConfiguration = new ClientBuilderConfiguration();
        clientBuilderConfiguration.setSignatureVersion(SignVersion.V4);
        // 初始化 ossClient 对象
        this.ossClient = OSSClientBuilder.create()
                .credentialsProvider(provider)
                .clientConfiguration(clientBuilderConfiguration)
                .region(ossConfig.getRegion())
                .endpoint(ossConfig.getEndpoint())
                .build();
        log.info("===== 阿里云 OSS 客户端初始化完成 =====");
    }

    /**
     * 销毁 OSS 客户端
     */
    @PreDestroy
    public void destroy() {
        if (ossClient == null) return;
        ossClient.shutdown();
        log.info("===== 阿里云 OSS 客户端已销毁 =====");
    }

    /**
     * 获取文件后缀（包含点号，如 .jpg）
     * @param originalFilename 原始文件名
     * @return 文件后缀，如果没有后缀则返回空字符串
     */
    private String getFileSuffix(String originalFilename) {
        if (originalFilename != null && originalFilename.contains(".")) {
            return originalFilename.substring(originalFilename.lastIndexOf(".")).toLowerCase();
        }
        return "";
    }

    /**
     * 上传文件到 OSS 服务器
     * @param objectFile MultipartFile 文件对象
     * @param virtualDir 文件上传的虚拟目录
     * <p>虚拟目录不加前斜杠，要加后斜杠，例如：markdowns/</p>
     * @return 上传的文件的可访问链接
     */
    public String uploadFile(MultipartFile objectFile, String virtualDir) {
        if (objectFile.isEmpty()) throw new RuntimeException("上传文件不能为空");

        // 原始文件名
        String originalFilename = objectFile.getOriginalFilename();
        // 获取文件后缀
        String suffix = getFileSuffix(originalFilename);
        // 生成唯一文件名
        String uniqueFilename = UUID.randomUUID() + suffix;
        // 文件保存的相对路径
        String fileSavedPath = virtualDir.isBlank() ? uniqueFilename : virtualDir +  uniqueFilename;

        try (InputStream inputStream = objectFile.getInputStream()) {
            // 上传文件
            ossClient.putObject(
                    ossConfig.getBucketName(),
                    fileSavedPath,
                    inputStream
            );
            // 构造文件可访问链接
            String baseUrl = "https://" + ossConfig.getBucketName() + "." + ossConfig.getEndpoint();
            String onlineFileUrl = baseUrl + "/" + (virtualDir.isBlank() ? "" : virtualDir) + uniqueFilename;
            log.info("===== 文件可访问链接：{} =====", onlineFileUrl);

            return onlineFileUrl;
        } catch (IOException e) {
            throw new RuntimeException("阿里云OSS文件上传异常：" + e.getMessage());
        }
    }

    /**
     * 上传文件到 OSS 服务器，使用文件后缀来分类文件
     * @param objectFile MultipartFile 文件对象
     * @return 上传的文件的可访问链接
     */
    public String uploadFile(MultipartFile objectFile) {
        if (objectFile.isEmpty()) throw new RuntimeException("上传文件不能为空");

        // 原始文件名
        String originalFilename = objectFile.getOriginalFilename();
        // 获取文件后缀
        String suffix = getFileSuffix(originalFilename);

        // 根据文件后缀确定虚拟目录
        String virtualDir = switch (suffix) {
            case ".md", ".markdown" -> "markdowns/";
            case ".jpg", ".jpeg", ".png", ".gif", ".bmp", ".webp", ".svg" -> "images/";
            case ".pdf" -> "pdfs/";
            case ".doc", ".docx" -> "documents/";
            case ".txt" -> "texts/";
            case ".csv", ".xls", ".xlsx" -> "tables/";
            default -> "others/";
        };

        return uploadFile(objectFile, virtualDir);
    }

}
