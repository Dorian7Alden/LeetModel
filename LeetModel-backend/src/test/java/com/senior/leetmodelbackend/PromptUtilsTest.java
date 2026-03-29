package com.senior.leetmodelbackend;

import com.senior.leetmodelbackend.pojo.entity.PromptTemplate.GenFullProblemSysPrompt;
import com.senior.leetmodelbackend.utils.PromptUtils;
import org.junit.jupiter.api.Test;

public class PromptUtilsTest {

    @Test
    public void buildPrompt() {

        String promptTemplate = PromptUtils.buildPrompt(GenFullProblemSysPrompt.builder()
                .difficulty("简单")
                .background("数组")
                .problem_type("单数")
                .build());

        System.out.println(promptTemplate);

    }


}
