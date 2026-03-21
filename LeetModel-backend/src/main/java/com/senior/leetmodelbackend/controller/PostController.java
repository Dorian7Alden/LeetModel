package com.senior.leetmodelbackend.controller;

import com.senior.leetmodelbackend.entity.dto.PostQueryDTO;
import com.senior.leetmodelbackend.entity.pojo.Result;
import com.senior.leetmodelbackend.entity.vo.PostQueryVO;
import com.senior.leetmodelbackend.service.PostService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/posts")
public class PostController {

    @Autowired
    private PostService postService;

    /**
     * 分页查询，条件筛选
     * 支持按 heat、likeCnt、viewCnt、commentCnt、createTime 排序
     * 支持按 type (experience|skill|discuss) 筛选
     * 支持按 title 或 content 关键词模糊匹配
     */
    @GetMapping
    public Result<PostQueryVO> getPostList(@ModelAttribute PostQueryDTO postQueryDTO) {
        PostQueryVO vo = postService.getPostList(postQueryDTO);
        return Result.success(vo);
    }

}
