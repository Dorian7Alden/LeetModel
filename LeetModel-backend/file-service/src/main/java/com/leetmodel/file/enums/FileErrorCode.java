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
    FILE_PURPOSE_INVALID(41305, "文件用途不合法"),
    FILE_CONTENT_TYPE_NOT_ALLOWED(41306, "当前用途不允许该文件类型"),
    FILE_BINDING_INVALID(41307, "文件绑定事件不合法"),
    FILE_OBJECT_KEY_NOT_ALLOWED(41308, "对象路径不在允许接管的交接目录内"),
    FILE_SIZE_MISMATCH(41309, "声明文件大小与对象实际大小不一致"),
    UPLOAD_SESSION_NOT_FOUND(41310, "上传会话不存在"),
    UPLOAD_SESSION_STATE_INVALID(41311, "当前上传会话状态不允许该操作"),
    UPLOAD_PART_INDEX_INVALID(41312, "分片序号超出范围"),
    UPLOAD_PART_INCOMPLETE(41313, "分片尚未全部上传或大小不一致"),
    UPLOAD_SIZE_EXCEEDED(41314, "文件大小超出当前允许范围"),
    STORAGE_UNAVAILABLE(51301, "对象存储暂不可用");

    private final int code;
    private final String message;
}
