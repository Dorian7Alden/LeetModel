package com.senior.leetmodelbackend.entity.vo;

import com.senior.leetmodelbackend.entity.pojo.Post;
import lombok.Data;

import java.util.List;

@Data
public class PostQueryVO {

    private List<Post> list;

    private Long total;

    private Integer pageNum;

    private Integer pageSize;

    private Integer totalPages;

}
