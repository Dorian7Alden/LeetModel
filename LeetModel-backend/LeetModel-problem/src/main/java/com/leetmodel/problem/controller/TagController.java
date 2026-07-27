package com.leetmodel.problem.controller;

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
 * 标签管理接口（需要 TAG_VIEW / TAG_MANAGE 权限）。
 *
 * @author LeetModel
 */
@RestController
@RequestMapping("/tags")
@RequiredArgsConstructor
@io.swagger.v3.oas.annotations.tags.Tag(name = "标签管理")
public class TagController {

    private final TagService tagService;

    @GetMapping
    @Operation(summary = "查询所有标签")
    public Result<List<Tag>> list() {
        List<Tag> tags = tagService.list();
        return Result.ok(tags);
    }

    @PostMapping
    @Operation(summary = "创建标签")
    public Result<Tag> create(@Valid @RequestBody TagRequest request) {
        Tag tag = tagService.createTag(request.getName());
        return Result.ok(tag);
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新标签")
    public Result<Tag> update(@PathVariable Long id,
                               @Valid @RequestBody TagRequest request) {
        Tag tag = tagService.updateTag(id, request.getName());
        return Result.ok(tag);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除标签")
    public Result<Void> delete(@PathVariable Long id) {
        tagService.removeById(id);
        return Result.ok();
    }
}
