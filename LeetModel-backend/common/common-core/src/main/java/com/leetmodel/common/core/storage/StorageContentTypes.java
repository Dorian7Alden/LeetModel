package com.leetmodel.common.core.storage;

import java.util.Set;

/**
 * 对象存储可由具体业务按需追加的媒体类型集合。
 */
public final class StorageContentTypes {

    /** 常见压缩包 MIME 类型 */
    public static final Set<String> ARCHIVE = Set.of(
            "application/zip",                // ZIP
            "application/x-zip-compressed",   // ZIP 兼容类型
            "application/vnd.rar",            // RAR
            "application/x-rar-compressed",   // RAR 兼容类型
            "application/x-7z-compressed",    // 7Z
            "application/x-tar",              // TAR
            "application/gzip",               // GZ、TGZ、TAR.GZ
            "application/x-gzip",             // GZ 兼容类型
            "application/x-bzip2",            // BZ2
            "application/x-xz"                // XZ
    );

    private StorageContentTypes() {
    }
}
