package com.leetmodel.problem.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.leetmodel.common.core.result.Result;
import com.leetmodel.problem.dto.TagRequest;
import com.leetmodel.problem.entity.Tag;
import com.leetmodel.problem.service.TagService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 标签管理接口（GET 需登录，POST/PUT/DELETE 需要 admin 角色）。
 *
 * @author LeetModel
 */
@RestController
@RequestMapping("/api/tags")
@RequiredArgsConstructor
@io.swagger.v3.oas.annotations.tags.Tag(name = "标签管理")
public class TagController {

    private final TagService tagService;

    @Operation(summary = "查询所有标签")
    @GetMapping
    public Result<List<Tag>> list() {
        List<Tag> tags = tagService.list();
        return Result.ok(tags);
    }

    @Operation(summary = "创建标签")
    @SaCheckRole("admin")
    @PostMapping
    public Result<Tag> create(@Valid @RequestBody TagRequest request) {
        Tag tag = tagService.createTag(request.getName());
        return Result.ok(tag);
    }

    @Operation(summary = "更新标签")
    @SaCheckRole("admin")
    @PutMapping("/{id}")
    public Result<Tag> update(@PathVariable Long id,
                               @Valid @RequestBody TagRequest request) {
        Tag tag = tagService.updateTag(id, request.getName());
        return Result.ok(tag);
    }

    @Operation(summary = "删除标签")
    @SaCheckRole("admin")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        tagService.removeById(id);
        return Result.ok();
    }
}
