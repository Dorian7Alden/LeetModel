package com.senior.leetmodelbackend.service;

import com.senior.leetmodelbackend.pojo.dto.PostQueryDTO;
import com.senior.leetmodelbackend.pojo.entity.Post;
import com.senior.leetmodelbackend.pojo.vo.PostQueryVO;
import com.senior.leetmodelbackend.mapper.PostMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PostService {

    @Autowired
    private PostMapper postMapper;

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
