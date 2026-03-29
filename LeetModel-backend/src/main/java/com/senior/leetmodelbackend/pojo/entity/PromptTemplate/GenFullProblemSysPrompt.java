package com.senior.leetmodelbackend.pojo.entity.PromptTemplate;

import com.senior.leetmodelbackend.pojo.enums.PromptEnums;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.HashMap;
import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = true)
public class GenFullProblemSysPrompt extends BasePrompt {

    private String difficulty;
    private String background;
    private String problem_type;

    @Builder
    public GenFullProblemSysPrompt(String difficulty, String background, String problem_type) {
        super(PromptEnums.GEN_FULL_PROBLEM_SYS_PROMPT);
        this.difficulty = difficulty;
        this.background = background;
        this.problem_type = problem_type;
    }

    @Override
    public Map<String, String> buildVariablesMap() {
        Map<String, String> variablesMap = new HashMap<>();
        variablesMap.put("difficulty", difficulty);
        variablesMap.put("background", background);
        variablesMap.put("problem_type", problem_type);
        return variablesMap;
    }
}
