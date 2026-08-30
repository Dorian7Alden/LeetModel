package com.leetmodel.assistant.tool.problem;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** recommend_problem 的模型可提交参数。 */
public record RecommendProblemInput(
        @Size(max = 50) String keyword,
        @Pattern(regexp = "[A-Z][A-Z0-9_]{0,31}") String contestCode,
        @Min(2000) @Max(2100) Integer year,
        @Min(1) @Max(3) Integer difficulty,
        @Pattern(regexp = "ZH|EN") String statementLanguage,
        @Min(30) @Max(10080) Integer maxDurationMinutes,
        @Min(1) @Max(5) Integer limit) {

    /** 可选关键词一旦出现就不能只有空白。 */
    @JsonIgnore
    @AssertTrue(message = "keyword 不能是空白字符串")
    public boolean isKeywordValid() {
        return keyword == null || !keyword.trim().isEmpty();
    }
}
