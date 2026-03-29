package com.senior.leetmodelbackend.pojo.enums.error;

import lombok.Getter;

/**
 * 社区与学习交流模块状态码 (05模块)
 * 涵盖发帖、评论、互动防刷等
 */
@Getter
public enum CommunityErrorCode implements BaseErrorCode {

    POST_NOT_FOUND(40501, "该帖子不存在或已被删除"),
    COMMENT_NOT_FOUND(40502, "该评论不存在或已被删除"),
    CONTENT_SENSITIVE(40503, "发布的内容包含违规敏感词汇，请修改后重试"),
    ACTION_TOO_FREQUENT(40504, "您的操作过于频繁，请稍后再试"),
    POST_AUTHOR_REQUIRED(40505, "无权操作，仅限原作者可执行此修改");

    private final int code;
    private final String msg;

    CommunityErrorCode(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }
}
