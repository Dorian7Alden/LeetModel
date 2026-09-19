package com.leetmodel.file.support;

import com.leetmodel.common.core.exception.BusinessException;
import com.leetmodel.file.enums.FileErrorCode;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 上传文件内容摘要计算。
 *
 * <p>摘要用于完整性核对与迁移对比，不触发自动去重，避免一个业务删除共享物理对象影响其他业务。</p>
 */
public final class FileContentDigest {

    private static final int BUFFER_SIZE = 8192;

    private FileContentDigest() {
    }

    /**
     * 计算文件内容的 SHA-256 十六进制摘要。
     *
     * @param file 待计算文件
     * @return 小写十六进制摘要
     * @throws BusinessException 当读取失败或算法不可用时
     */
    public static String sha256(MultipartFile file) {
        try (InputStream inputStream = file.getInputStream()) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[BUFFER_SIZE];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException | NoSuchAlgorithmException exception) {
            throw new BusinessException(FileErrorCode.STORAGE_UNAVAILABLE, "文件内容摘要计算失败");
        }
    }
}
