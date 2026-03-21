package com.senior.leetmodelbackend.entity.dto;

import lombok.Data;

@Data
public class PostQueryDTO {

    Integer pageSize = 10;
    Integer pageNum = 1;

    String sortField;

    String sortOrder = "desc";

    String type;

    String keyword;

    public int getOffset() {
        return pageSize * (pageNum - 1);
    }

}
