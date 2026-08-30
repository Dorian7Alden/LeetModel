package com.leetmodel.submission.enums;
import com.leetmodel.common.core.exception.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
@Getter @AllArgsConstructor
public enum SubmissionErrorCode implements ErrorCode {
    TEAM_NOT_AVAILABLE(40601, "队伍不存在或暂不可用"),
    NOT_TEAM_MEMBER(40602, "只有队伍成员可以提交和查看论文"),
    PRACTICE_NOT_STARTED(40603, "练习开始后才能提交"),
    DEADLINE_PASSED(40604, "提交截止时间已过"),
    PDF_ONLY(40605, "首版仅支持 PDF 文件"),
    SUBMISSION_NOT_FOUND(40606, "提交记录不存在"),
    FINAL_SUBMISSION_NOT_FOUND(40607, "没有可锁定的成功提交"),
    DEADLINE_NOT_REACHED(40608, "截止时间尚未到达"),
    SUBMISSION_PERMISSION_DENIED(40609, "当前成员没有作品提交权限"),
    PDF_SIZE_EXCEEDED(40610, "PDF 文件大小不能超过 20MB"),
    UPLOAD_NOT_FOUND(40611, "上传会话不存在"),
    UPLOAD_ALREADY_ACTIVE(40612, "队伍已有其他成员正在上传论文"),
    UPLOAD_STATE_INVALID(40613, "当前上传状态不允许该操作"),
    UPLOAD_EXPIRED(40614, "上传会话已过期，请重新开始"),
    CHUNK_INDEX_INVALID(40615, "分片序号不正确"),
    CHUNK_SIZE_INVALID(40616, "分片大小不正确"),
    CHUNK_CHECKSUM_MISMATCH(40617, "分片内容校验失败"),
    CHUNK_CONFLICT(40618, "该分片已上传且内容不一致"),
    CHUNK_MISSING(40619, "分片尚未全部上传"),
    FILE_CHECKSUM_MISMATCH(40620, "合并后的 PDF 内容校验失败"),
    UPLOAD_COMPLETING(40621, "论文正在合并，请稍后重试"),
    REVIEW_TASK_CREATE_FAILED(50601, "评审任务创建失败");
    private final int code; private final String message;
}
