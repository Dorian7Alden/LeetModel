package com.senior.leetmodelbackend.pojo.enums;

import lombok.Getter;

@Getter
public enum PromptEnums {

    GEN_FULL_PROBLEM_SYS_PROMPT("生成套题的系统提示词", "PromptTemplates/GenFullPromblemSysPrompt.md"),
    GEN_FULL_PROBLEM_USER_PROMPT("生成套题的用户提示词", "PromptTemplates/GenFullPromblemUserPrompt.md"),
    REVIEW_PROBLEM_SYS_PROMPT("审核答题的系统提示词", "PromptTemplates/ReviewProblemSysPrompt.md");
//    REVIEW_PROBLEM_USER_PROMPT("审核答题的用户提示词", "PromptTemplates/ReviewProblemSysPrompt.md");

    private final String description;
    private final String path;

    PromptEnums(String description, String path) {
        this.description = description;
        this.path = path;
    }

}
