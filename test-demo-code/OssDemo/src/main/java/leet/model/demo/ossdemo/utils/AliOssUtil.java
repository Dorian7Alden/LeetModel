package leet.model.demo.ossdemo.utils;

import com.aliyun.oss.*;
import com.aliyun.oss.common.auth.*;
import com.aliyun.oss.common.comm.SignVersion;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import leet.model.demo.ossdemo.properties.AliOssProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

@Slf4j
@Component
public class AliOssUtil {

    private final AliOssProperties aliOssProperties;

    /**
     * 单例 OSS 客户端（全局唯一，项目启动创建，关闭销毁）
     */
    private OSS ossClient;

    public AliOssUtil(AliOssProperties aliOssProperties) {
        this.aliOssProperties = aliOssProperties;
    }

    /**
     * 项目启动时：初始化单例 OSS 客户端（只执行一次）
     */
    @PostConstruct
    public void init() {
        // 创建凭证提供者
        DefaultCredentialProvider provider = new DefaultCredentialProvider(aliOssProperties.getAccessKeyId(), aliOssProperties.getAccessKeySecret());
        ClientBuilderConfiguration clientBuilderConfiguration = new ClientBuilderConfiguration();
        clientBuilderConfiguration.setSignatureVersion(SignVersion.V4);
        // 初始化 ossClient 对象
        this.ossClient = OSSClientBuilder.create()
                .credentialsProvider(provider)
                .clientConfiguration(clientBuilderConfiguration)
                .region(aliOssProperties.getRegion())
                .endpoint(aliOssProperties.getEndpoint())
                .build();
        log.info("=== 阿里云OSS单例客户端初始化完成 ===");
    }

    /**
     * 项目关闭时：销毁 OSS 客户端（只执行一次）
     */
    @PreDestroy
    public void destroy() {
        if (ossClient != null) {
            ossClient.shutdown();
            log.info("=== 阿里云OSS单例客户端已销毁 ===");
        }
    }

    /**
     * 上传文件到 OSS 服务器
     * @param objectFile MultipartFile 文件对象
     * @param virtualDir 文件上传的虚拟目录，不加前斜杠，要加后斜杠，例如：markdowns/
     * @return 上传的文件的可访问链接
     */
    public String uploadFile(MultipartFile objectFile, String virtualDir) {
        if (objectFile.isEmpty()) throw new RuntimeException("上传文件不能为空");

        // 原始文件名
        String originalFilename = objectFile.getOriginalFilename();
        // 获取文件后缀
        String suffix = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            suffix += originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        // 生成唯一文件名
        String uniqueFilename = UUID.randomUUID() + suffix;
        // 文件保存的相对路径
        String fileSavedPath = virtualDir.isBlank() ? uniqueFilename : virtualDir +  uniqueFilename;

        try (InputStream inputStream = objectFile.getInputStream()) {
            // 上传文件
            ossClient.putObject(
                    aliOssProperties.getBucketName(),
                    fileSavedPath,
                    inputStream
            );
            // 构造文件可访问链接
            String baseUrl = "https://" + aliOssProperties.getBucketName() + "." + aliOssProperties.getEndpoint();
            String onlineFileUrl = baseUrl + "/" + (virtualDir.isBlank() ? "" : virtualDir) + uniqueFilename;
            log.info("=== 文件可访问链接：{} ===", onlineFileUrl);

            return onlineFileUrl;
        } catch (IOException e) {
            throw new RuntimeException("阿里云OSS文件上传异常：" + e.getMessage());
        }
    }

    /**
     * 上传文件到 OSS 服务器，默认上传到根目录
     * @param objectFile MultipartFile 文件对象
     * @return 上传的文件的可访问链接
     */
    public String uploadFile(MultipartFile objectFile) {
        return uploadFile(objectFile, "");
    }
}