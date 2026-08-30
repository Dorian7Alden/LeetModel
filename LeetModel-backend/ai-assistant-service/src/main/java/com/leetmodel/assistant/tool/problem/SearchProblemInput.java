package com.leetmodel.assistant.tool.problem;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/** search_problem 的模型可提交参数。 */
public record SearchProblemInput(
        @Min(1001) @Max(10000) Integer code,
        @Size(max = 50) String keyword,
        Boolean includeOverview,
        @Min(1) @Max(5) Integer limit) {

    /** 校验题号和关键词二选一，并锁定题号查询条数。 */
    @JsonIgnore
    @AssertTrue(message = "code 与 keyword 必须二选一，题号查询 limit 只能为 1")
    public boolean isSearchConditionValid() {
        boolean hasKeyword = keyword != null && !keyword.trim().isEmpty();
        if (!(code != null ^ hasKeyword)) return false;
        return code == null || limit == null || limit == 1;
    }
}
