package com.senior.leetmodelbackend;

import com.senior.leetmodelbackend.pojo.entity.PromptTemplate.GenFullProblemSysPrompt;
import com.senior.leetmodelbackend.pojo.entity.PromptTemplate.GenFullProblemUserPrompt;
import com.senior.leetmodelbackend.utils.ArkAiUtils;
import com.senior.leetmodelbackend.utils.PromptUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class AiClientTest {

    @Autowired
    private ArkAiUtils arkAiUtils;

    @Autowired
    private PromptUtils promptUtils;

    @Test
    public void testSingleChat() {
        String response = arkAiUtils.chat(
                promptUtils.buildPrompt(GenFullProblemSysPrompt.builder().build()),
                promptUtils.buildPrompt( GenFullProblemUserPrompt.builder()
                                .difficulty("简单")
                                .data_feature("大规模数据")
                                .problem_type("连续建模")
                                .industry("环境生态")
                                .build()
                ));

        System.out.println(response);
    }


}
