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
    REVIEW_TASK_CREATE_FAILED(50601, "评审任务创建失败");
    private final int code; private final String message;
}
