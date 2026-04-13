package leet.model.demo.ossdemo.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "aliyun.oss")
public class AliOssProperties {

    /**
     * OSS地域节点
     */
    private String endpoint;

    /**
     * 密钥ID
     */
    private String accessKeyId;

    /**
     * 密钥秘钥
     */
    private String accessKeySecret;

    /**
     * 存储空间名称
     */
    private String bucketName;

    /**
     * 访问地域
     */
    private String region;

}
