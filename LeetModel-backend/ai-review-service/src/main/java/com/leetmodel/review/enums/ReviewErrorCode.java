package com.leetmodel.review.enums;
import com.leetmodel.common.core.exception.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
@Getter @AllArgsConstructor
public enum ReviewErrorCode implements ErrorCode {
    TASK_NOT_FOUND(40701, "评审任务不存在"), NOT_TEAM_MEMBER(40702, "只有队伍成员可以查看评审结果"),
    TASK_NOT_FAILED(40703, "只有失败任务可以重试"), RESULT_NOT_FOUND(40704, "评审结果尚未产生"),
    DEPENDENCY_UNAVAILABLE(50701, "评审依赖服务暂不可用");
    private final int code; private final String message;
}
