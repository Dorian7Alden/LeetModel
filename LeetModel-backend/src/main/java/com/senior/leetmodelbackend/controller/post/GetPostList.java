package com.senior.leetmodelbackend.controller.post;

import com.senior.leetmodelbackend.pojo.dto.PostQueryDTO;
import com.senior.leetmodelbackend.pojo.entity.Result;
import com.senior.leetmodelbackend.pojo.vo.PostQueryVO;
import com.senior.leetmodelbackend.service.PostService;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RestController;

@RestController
@AllArgsConstructor
public class GetPostList extends PostController {

    private final PostService postService;

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
