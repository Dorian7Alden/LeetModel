package com.senior.leetmodelbackend.entity.pojo;

import lombok.Data;

@Data
public class Problem {

    Integer id;
    String title;
    String content;
    String difficulty;
    String source;
    String status;
    String language;
    String dataUrl;
    String creatorId;
    String createTime;
    String updateTime;
    Integer aveScore;
}


