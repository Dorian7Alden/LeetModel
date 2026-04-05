package com.senior.leetmodelbackend.pojo.entity.PromptResponse;

import lombok.Data;

@Data
public class GenFullProblemPromptResponse {

    private String title;
    private String background;
    private String requirements;
    private String submissionRequirements;
    private String references;

}
