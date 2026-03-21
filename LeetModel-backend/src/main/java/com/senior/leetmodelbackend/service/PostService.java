package com.senior.leetmodelbackend.service;

import com.senior.leetmodelbackend.entity.dto.PostQueryDTO;
import com.senior.leetmodelbackend.entity.vo.PostQueryVO;

public interface PostService {

    PostQueryVO getPostList(PostQueryDTO postQueryDTO);

}
