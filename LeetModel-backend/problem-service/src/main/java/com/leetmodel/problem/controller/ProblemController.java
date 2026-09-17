package com.leetmodel.problem.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.leetmodel.common.core.result.Result;
import com.leetmodel.common.core.result.PageResult;
import com.leetmodel.common.security.context.UserContext;
import com.leetmodel.problem.dto.ProblemCreateRequest;
import com.leetmodel.problem.dto.ProblemPageQuery;
import com.leetmodel.problem.dto.ProblemUpdateRequest;
import com.leetmodel.problem.service.ProblemService;
import com.leetmodel.problem.vo.ProblemVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.validation.annotation.Validated;

/**
 * 题目管理接口（需要 admin 角色）。
 */
@RestController
@RequestMapping("/api/problems")
@RequiredArgsConstructor
@SaCheckRole("admin")
@Tag(name = "题目管理")
@Validated
public class ProblemController {

    private final ProblemService problemService;

    /**
     * 管理员分页组合条件查询题目列表。
     *
     * @param query 分页与组合筛选参数对象，不能为 null
     * @return 分页包装的题目视图列表
     */
    @Operation(summary = "分页查询题目")
    @GetMapping
    public Result<PageResult<ProblemVO>> page(@Valid ProblemPageQuery query) {
        IPage<ProblemVO> page = problemService.pageProblems(query);
        return Result.ok(PageResult.from(page));
    }

    /**
     * 管理员查询指定题目的全量详情（含未发布状态与附件列表）。
     *
     * @param id 目标题目 ID，不能为 null
     * @return 题目详情视图对象
     */
    @Operation(summary = "查询题目详情")
    @GetMapping("/{id}")
    public Result<ProblemVO> detail(@PathVariable Long id) {
        ProblemVO vo = problemService.getProblemDetail(id);
        return Result.ok(vo);
    }

    /**
     * 管理员录入并创建新的建模题目。
     *
     * @param request 包含题目标题、题面 Markdown 与元数据的请求对象，不能为 null
     * @return 创建成功后的题目视图对象
     */
    @Operation(summary = "创建题目")
    @PostMapping
    public Result<ProblemVO> create(@Valid @RequestBody ProblemCreateRequest request) {
        Long creatorId = UserContext.getUserId();
        ProblemVO vo = problemService.createProblem(request, creatorId);
        return Result.ok(vo);
    }

    /**
     * 管理员更新题目的基本信息、题面内容或发布状态。
     *
     * @param id      目标题目 ID，不能为 null
     * @param request 包含修改属性的请求对象，不能为 null
     * @return 更新后的题目视图对象
     */
    @Operation(summary = "更新题目")
    @PutMapping("/{id}")
    public Result<ProblemVO> update(@PathVariable Long id,
                                     @Valid @RequestBody ProblemUpdateRequest request) {
        ProblemVO vo = problemService.updateProblem(id, request);
        return Result.ok(vo);
    }

    /**
     * 管理员逻辑删除指定题目。
     *
     * @param id 目标题目 ID，不能为 null
     * @return 统一成功空响应
     */
    @Operation(summary = "删除题目（逻辑删除）")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        problemService.deleteProblem(id);
        return Result.ok();
    }

    /**
     * 为指定题目上传附件资料（PDF、数据集等）。
     *
     * @param id          目标题目 ID，不能为 null
     * @param file        待上传的附件文件，不能为 null
     * @param description 可选的附件描述说明
     * @param sortOrder   可选的展示排序序号
     * @return 上传成功后的附件视图对象
     */
    @Operation(summary = "上传题目附件")
    @PostMapping(path = "/{id}/attachments", consumes = "multipart/form-data")
    public Result<ProblemVO.AttachmentVO> uploadAttachment(
            @PathVariable Long id,
            @RequestPart("file") MultipartFile file,
            @RequestParam(required = false) @Size(max = 500) String description,
            @RequestParam(required = false) @Min(0) Integer sortOrder
    ) {
        return Result.ok(problemService.uploadAttachment(id, file, description, sortOrder));
    }

    /**
     * 删除指定题目下关联的附件文件。
     *
     * @param problemId    目标题目 ID，不能为 null
     * @param attachmentId 目标附件记录 ID，不能为 null
     * @return 统一成功空响应
     */
    @Operation(summary = "删除题目附件")
    @DeleteMapping("/{problemId}/attachments/{attachmentId}")
    public Result<Void> deleteAttachment(
            @PathVariable Long problemId,
            @PathVariable Long attachmentId
    ) {
        problemService.deleteAttachment(problemId, attachmentId);
        return Result.ok();
    }
}
