package com.leetmodel.common.api.feign;

import com.leetmodel.common.api.dto.FileAccessUrlDTO;
import com.leetmodel.common.core.result.Result;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * 按 fileId 读取受控文件内容。
 *
 * <p>内部消费者不持有对象路径与长期凭证：先向 file-service 申请有界时效访问地址，
 * 再按流式方式读取对象内容。</p>
 */
public class FileContentClient {

    /** 统一响应体成功状态码。 */
    private static final int RESULT_SUCCESS_CODE = 20000;
    /** 建连超时，避免存储不可用时长时间占用调用线程。 */
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);

    private final FileFeignClient fileFeignClient;
    private final HttpClient httpClient;

    public FileContentClient(FileFeignClient fileFeignClient) {
        this.fileFeignClient = fileFeignClient;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
    }

    /**
     * 打开文件内容输入流。
     *
     * @param fileId 文件资产标识
     * @return 文件内容输入流，调用方负责关闭
     * @throws IOException 访问地址申请失败或对象读取失败时
     */
    public InputStream open(Long fileId) throws IOException {
        if (fileId == null) {
            throw new IOException("文件资产标识为空");
        }
        String url;
        try {
            Result<FileAccessUrlDTO> result = fileFeignClient.createAccessUrl(fileId);
            if (result == null || result.getCode() != RESULT_SUCCESS_CODE || result.getData() == null) {
                throw new IOException("文件访问地址生成失败");
            }
            url = result.getData().url();
        } catch (RuntimeException exception) {
            throw new IOException("文件访问地址申请异常", exception);
        }
        HttpResponse<InputStream> response;
        try {
            response = httpClient.send(
                    HttpRequest.newBuilder(URI.create(url)).GET().build(),
                    HttpResponse.BodyHandlers.ofInputStream());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("文件读取被中断", exception);
        }
        if (response.statusCode() != 200) {
            try (InputStream body = response.body()) {
                throw new IOException("文件读取失败，状态码=" + response.statusCode());
            }
        }
        return response.body();
    }
}
