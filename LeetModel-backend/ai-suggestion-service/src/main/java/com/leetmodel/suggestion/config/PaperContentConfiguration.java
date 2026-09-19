package com.leetmodel.suggestion.config;

import com.leetmodel.common.api.feign.FileContentClient;
import com.leetmodel.common.api.feign.FileFeignClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 论文内容读取配置。
 *
 * <p>建议链路只按 fileId 读取受控内容，不持有对象路径与长期凭证。</p>
 */
@Configuration(proxyBeanMethods = false)
public class PaperContentConfiguration {

    /**
     * 创建按 fileId 读取文件内容的客户端。
     *
     * @param fileFeignClient 文件服务内部客户端
     * @return 文件内容客户端
     */
    @Bean
    public FileContentClient fileContentClient(FileFeignClient fileFeignClient) {
        return new FileContentClient(fileFeignClient);
    }
}
