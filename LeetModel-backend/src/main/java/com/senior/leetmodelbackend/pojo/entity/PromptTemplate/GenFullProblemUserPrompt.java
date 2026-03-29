package com.senior.leetmodelbackend.pojo.entity.PromptTemplate;

import com.senior.leetmodelbackend.pojo.enums.PromptEnums;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.HashMap;
import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = true)
public class GenFullProblemUserPrompt extends BasePrompt {

    private String difficulty;
    private String industry;
    private String problem_type;
    private String data_feature;

    @Builder
    public GenFullProblemUserPrompt(String difficulty, String industry, String problem_type, String data_feature) {
        super(PromptEnums.GEN_FULL_PROBLEM_USER_PROMPT);
        this.difficulty = difficulty;
        this.industry = industry;
        this.problem_type = problem_type;
        this.data_feature = data_feature;
    }

    @Override
    public Map<String, String> buildVariablesMap() {
        Map<String, String> variablesMap = new HashMap<>();
        variablesMap.put("difficulty", difficulty);
        variablesMap.put("industry", industry);
        variablesMap.put("problem_type", problem_type);
        variablesMap.put("data_feature", data_feature);
        return variablesMap;
    }
}

