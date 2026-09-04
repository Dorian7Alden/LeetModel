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
 */
@RestController
@RequestMapping("/api/tags")
@RequiredArgsConstructor
@io.swagger.v3.oas.annotations.tags.Tag(name = "标签管理")
public class TagController {

    private final TagService tagService;

    /**
     * 查询系统全部领域知识标签列表。
     *
     * @return 标签实体列表
     */
    @Operation(summary = "查询所有标签")
    @GetMapping
    public Result<List<Tag>> list() {
        List<Tag> tags = tagService.list();
        return Result.ok(tags);
    }

    /**
     * 管理员录入并创建新的知识标签。
     *
     * @param request 包含标签名称与类型的请求对象，不能为 null
     * @return 创建成功后的标签实体
     */
    @Operation(summary = "创建标签")
    @SaCheckRole("admin")
    @PostMapping
    public Result<Tag> create(@Valid @RequestBody TagRequest request) {
        Tag tag = tagService.createTag(request.getName(), request.getType());
        return Result.ok(tag);
    }

    /**
     * 管理员更新已有标签的名称或分类。
     *
     * @param id      目标标签 ID，不能为 null
     * @param request 包含待更新属性的请求对象，不能为 null
     * @return 更新后的标签实体
     */
    @Operation(summary = "更新标签")
    @SaCheckRole("admin")
    @PutMapping("/{id}")
    public Result<Tag> update(@PathVariable Long id,
                               @Valid @RequestBody TagRequest request) {
        Tag tag = tagService.updateTag(id, request.getName(), request.getType());
        return Result.ok(tag);
    }

    /**
     * 管理员删除未被任何题目引用的标签。
     *
     * @param id 目标标签 ID，不能为 null
     * @return 统一成功空响应
     */
    @Operation(summary = "删除标签")
    @SaCheckRole("admin")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        tagService.deleteTag(id);
        return Result.ok();
    }
}
