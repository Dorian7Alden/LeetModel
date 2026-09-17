package com.leetmodel.file.enums;

import com.leetmodel.common.core.exception.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum FileErrorCode implements ErrorCode {
    FILE_NOT_FOUND(41301, "文件资产不存在"),
    GROUP_PATH_INVALID(41302, "文件分组路径不合法"),
    FILE_NOT_DELETABLE(41303, "该文件资产不允许删除"),
    FILE_STATUS_INVALID(41304, "文件资产状态不允许执行当前操作"),
    STORAGE_UNAVAILABLE(51301, "对象存储暂不可用");

    private final int code;
    private final String message;
}
