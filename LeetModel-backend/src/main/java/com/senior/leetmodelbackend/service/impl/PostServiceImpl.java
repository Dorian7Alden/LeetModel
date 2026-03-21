package com.senior.leetmodelbackend.service.impl;

import com.senior.leetmodelbackend.entity.dto.PostQueryDTO;
import com.senior.leetmodelbackend.entity.pojo.Post;
import com.senior.leetmodelbackend.entity.vo.PostQueryVO;
import com.senior.leetmodelbackend.mapper.PostMapper;
import com.senior.leetmodelbackend.service.PostService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PostServiceImpl implements PostService {

    @Autowired
    private PostMapper postMapper;

    @Override
    public PostQueryVO getPostList(PostQueryDTO postQueryDTO) {
        List<Post> list = postMapper.selectPostList(postQueryDTO);
        Long total = postMapper.selectPostCount(postQueryDTO);

        PostQueryVO vo = new PostQueryVO();
        vo.setList(list);
        vo.setTotal(total);
        vo.setPageNum(postQueryDTO.getPageNum());
        vo.setPageSize(postQueryDTO.getPageSize());
        vo.setTotalPages((int) Math.ceil((double) total / postQueryDTO.getPageSize()));

        return vo;
    }

}
