package com.leetmodel.common.core.storage;

import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.Set;

/**
 * 对象存储服务接口。
 *
 * <p>统一抽象文件上传、流式下载、临时访问预签名 URL 生成与文件删除；隔离具体存储厂商 SDK。</p>
 */
public interface StorageService {

    /**
     * 上传文件至默认 files 目录。
     *
     * @param file 待上传的文件对象，不能为空
     * @return 对象在存储桶中的唯一标识路径
     */
    String upload(MultipartFile file);

    /**
     * 上传文件至指定业务目录前缀。
     *
     * @param file   待上传的文件对象，不能为空
     * @param prefix 目标业务目录前缀，如 avatars、problems
     * @return 格式为 {prefix}/{UUID}.{ext} 的唯一对象路径
     */
    String upload(MultipartFile file, String prefix);

    /**
     * 使用业务额外允许的媒体类型将文件上传至指定目录。
     *
     * <p>额外类型只对本次调用生效，不会放宽头像、论文等其他上传入口的基础白名单。</p>
     *
     * @param file                          待上传的文件对象，不能为空
     * @param prefix                        目标业务目录前缀，不能为空
     * @param additionalAllowedContentTypes 当前业务额外允许的 MIME 类型集合，不能为 null
     * @return 格式为 {prefix}/{UUID}.{ext} 的唯一对象路径
     */
    String upload(
            MultipartFile file,
            String prefix,
            Set<String> additionalAllowedContentTypes
    );

    /**
     * 流式读取文件内容。
     *
     * @param objectName 存储桶中的对象唯一标识路径，不能为空
     * @return 目标文件的二进制输入流
     */
    InputStream download(String objectName);

    /**
     * 生成带签名的临时文件访问 URL。
     *
     * @param objectName 存储桶中的对象唯一标识路径，不能为空
     * @return 具备访问时效的预签名 GET 地址
     */
    String getUrl(String objectName);

    /**
     * 从存储桶中物理删除指定文件。
     *
     * @param objectName 待删除的对象唯一标识路径，不能为空
     */
    void delete(String objectName);
}
