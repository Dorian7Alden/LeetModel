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

    @Builder
    public GenFullProblemSysPrompt() {
        super(PromptEnums.GEN_FULL_PROBLEM_SYS_PROMPT);
    }

    @Override
    public Map<String, String> buildVariablesMap() {
        return new HashMap<>();
    }
}
