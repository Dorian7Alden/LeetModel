package com.senior.leetmodelbackend.entity.pojo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class Post {
    Integer id;
    Integer publisherId;
    String type;
    String title;
    String content;
    Integer likeCnt;
    Integer commentCnt;
    Integer viewCnt;
    Integer heat;
    String status;
    LocalDateTime createTime;
    LocalDateTime updateTime;
}
