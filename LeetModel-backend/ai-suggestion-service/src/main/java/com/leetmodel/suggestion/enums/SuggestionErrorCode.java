package com.leetmodel.suggestion.enums;

import com.leetmodel.common.core.exception.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum SuggestionErrorCode implements ErrorCode {
    NOT_TEAM_MEMBER(40801, "只有队伍成员可以生成和查看论文建议"),
    FINAL_SUBMISSION_REQUIRED(40802, "只有已经确定的最终论文可以生成建议"),
    REVIEW_NOT_READY(40803, "AI 评审完成后才能生成论文建议"),
    TASK_NOT_FOUND(40804, "论文建议任务不存在"),
    TASK_NOT_FAILED(40805, "只有失败的论文建议任务可以重试"),
    PDF_TEXT_EMPTY(40806, "论文未提取到可用文字，请确认 PDF 不是纯扫描件"),
    DEPENDENCY_UNAVAILABLE(50801, "论文建议依赖服务暂不可用"),
    SOURCE_DATA_INVALID(50802, "论文建议源数据不完整或互相矛盾");

    private final int code;
    private final String message;
}
