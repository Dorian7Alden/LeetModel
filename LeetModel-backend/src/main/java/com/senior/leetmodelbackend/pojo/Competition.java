package com.senior.leetmodelbackend.pojo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class Competition {

    private int id;
    private String title;
    private String language;
    private String introduction;

    private LocalDateTime signUpStartTime;
    private LocalDateTime signUpEndTime;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String officialUrl;
    private String imageUrl;
    private String status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

}
