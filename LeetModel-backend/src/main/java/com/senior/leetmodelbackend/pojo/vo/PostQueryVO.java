package com.senior.leetmodelbackend.pojo.vo;

import com.senior.leetmodelbackend.pojo.entity.Post;
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
