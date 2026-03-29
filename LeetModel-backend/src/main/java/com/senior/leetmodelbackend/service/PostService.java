package com.senior.leetmodelbackend.service;

import com.senior.leetmodelbackend.pojo.dto.PostQueryDTO;
import com.senior.leetmodelbackend.pojo.vo.PostQueryVO;

public interface PostService {

    PostQueryVO getPostList(PostQueryDTO postQueryDTO);

}
