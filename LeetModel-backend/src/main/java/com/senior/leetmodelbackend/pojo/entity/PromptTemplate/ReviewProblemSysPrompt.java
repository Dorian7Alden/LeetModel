package com.senior.leetmodelbackend.pojo.entity.PromptTemplate;

import com.senior.leetmodelbackend.pojo.enums.PromptEnums;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.HashMap;
import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = true)
public class ReviewProblemSysPrompt extends BasePrompt {

    private String problem_info;
    private String answer_content;

    @Builder
    public ReviewProblemSysPrompt(String problem_info, String answer_content) {
        super(PromptEnums.REVIEW_PROBLEM_SYS_PROMPT);
        this.problem_info = problem_info;
        this.answer_content = answer_content;
    }

    @Override
    public Map<String, String> buildVariablesMap() {
        Map<String, String> variablesMap = new HashMap<>();
        variablesMap.put("problem_info", problem_info);
        variablesMap.put("answer_content", answer_content);
        return variablesMap;
    }
}
