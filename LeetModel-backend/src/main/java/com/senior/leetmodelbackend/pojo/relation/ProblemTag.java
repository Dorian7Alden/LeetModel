package com.senior.leetmodelbackend.pojo.relation;

import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ProblemTag {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    Integer id;
    Integer problemId;
    Integer tagId;
    LocalDateTime createTime;

}
