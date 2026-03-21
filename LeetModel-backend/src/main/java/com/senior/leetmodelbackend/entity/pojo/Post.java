package com.senior.leetmodelbackend.entity.pojo;

import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class Post {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Integer id;
    @NotNull
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
