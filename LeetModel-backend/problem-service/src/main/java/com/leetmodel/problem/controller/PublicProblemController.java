package com.leetmodel.problem.controller;

import com.leetmodel.common.core.result.Result;
import com.leetmodel.common.core.result.PageResult;
import com.leetmodel.problem.dto.ProblemPageQuery;
import com.leetmodel.problem.service.ProblemService;
import com.leetmodel.problem.cache.ProblemPublicCacheService;
import com.leetmodel.problem.vo.ProblemVO;
import com.leetmodel.problem.vo.ProblemFilterOptionsVO;
import com.leetmodel.common.cache.HttpCacheSupport;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 题目公开接口（无需认证，仅返回已发布题目）。
 */
@RestController
@RequestMapping("/api/public/problems")
@RequiredArgsConstructor
@io.swagger.v3.oas.annotations.tags.Tag(name = "题目浏览")
public class PublicProblemController {

    private final ProblemService problemService;
    private final ProblemPublicCacheService publicCacheService;

    @Operation(summary = "查询公开题库筛选项")
    @GetMapping("/filter-options")
    public ResponseEntity<Result<ProblemFilterOptionsVO>> filterOptions(
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch
    ) {
        HttpCacheSupport.Validator validator = publicCacheService.filterValidator();
        if (validator.matches(ifNoneMatch)) return validator.notModified();
        return validator.ok(Result.ok(publicCacheService.filterOptions()));
    }

    @Operation(summary = "分页浏览已发布题目")
    @GetMapping
    public ResponseEntity<Result<PageResult<ProblemVO>>> page(
            @Valid ProblemPageQuery query,
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch
    ) {
        query.setStatus(1);
        if (!publicCacheService.isPageCacheable(query)) {
            return noStore(Result.ok(publicCacheService.page(query)));
        }
        HttpCacheSupport.Validator validator = publicCacheService.pageValidator(query);
        if (validator.matches(ifNoneMatch)) return validator.notModified();
        return validator.ok(Result.ok(publicCacheService.page(query)));
    }

    @Operation(summary = "浏览题目详情")
    @GetMapping("/{id}")
    public ResponseEntity<Result<ProblemVO>> detail(
            @PathVariable Long id,
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch
    ) {
        HttpCacheSupport.Validator validator = publicCacheService.detailValidator(id);
        if (validator.matches(ifNoneMatch)) return validator.notModified();
        return validator.ok(Result.ok(publicCacheService.detail(id)));
    }

    @Operation(summary = "随机获取已发布题目")
    @GetMapping("/random")
    public ResponseEntity<Result<ProblemVO>> random(@Valid ProblemPageQuery query) {
        return noStore(Result.ok(problemService.getRandomPublishedProblem(query)));
    }

    /**
     * 返回禁止任何中间层存储的响应。
     *
     * @param body 响应体
     * @param <T> 数据类型
     * @return no-store 响应
     */
    private <T> ResponseEntity<Result<T>> noStore(Result<T> body) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(body);
    }
}
