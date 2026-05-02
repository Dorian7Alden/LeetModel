package com.senior.leetmodelbackend.pojo.dto.tag;

import lombok.Data;

/**
 * 为 problem 表创建标签关联
 */
@Data
public class CreateProblemTagDTO {

    private Integer tagId;
    private Integer categoryId;
    private String tagName;

}
