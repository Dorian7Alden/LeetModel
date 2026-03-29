package com.senior.leetmodelbackend;

import com.senior.leetmodelbackend.pojo.entity.PromptTemplate.GenFullProblemSysPrompt;
import com.senior.leetmodelbackend.pojo.entity.PromptTemplate.GenFullProblemUserPrompt;
import com.senior.leetmodelbackend.utils.PromptUtils;
import org.junit.jupiter.api.Test;

public class PromptUtilsTest {

    private final PromptUtils promptUtils = new PromptUtils();

    @Test
    public void buildPrompt() {

        String promptTemplate = promptUtils.buildPrompt(GenFullProblemUserPrompt.builder()
                .difficulty("简单")
                .data_feature("大规模数据")
                .problem_type("连续建模")
                .industry("环境生态")
                .build());

        System.out.println(promptTemplate);

        System.out.println();
        System.out.println("=========================================================================================");
        System.out.println("=========================================================================================");
        System.out.println("=========================================================================================");
        System.out.println();

        promptTemplate = promptUtils.buildPrompt(GenFullProblemSysPrompt.builder().build());
        System.out.println(promptTemplate);

    }


}
