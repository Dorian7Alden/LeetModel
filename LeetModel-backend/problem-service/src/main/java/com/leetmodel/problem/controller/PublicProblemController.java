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

    /**
     * 查询公开题库的年份、难度、赛事与标签筛选项集合（支持 HTTP ETag 协商缓存）。
     *
     * @param ifNoneMatch 请求头 ETag 缓存标识，可为空
     * @return 包含筛选项数据的 HTTP 响应实体
     */
    @Operation(summary = "查询公开题库筛选项")
    @GetMapping("/filter-options")
    public ResponseEntity<Result<ProblemFilterOptionsVO>> filterOptions(
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch
    ) {
        HttpCacheSupport.Validator validator = publicCacheService.filterValidator();
        if (validator.matches(ifNoneMatch)) return validator.notModified();
        return validator.ok(Result.ok(publicCacheService.filterOptions()));
    }

    /**
     * 客户端免登录分页浏览已发布的题目列表（结合 ETag 协商缓存与多级缓存）。
     *
     * @param query       分页与检索条件，不能为 null
     * @param ifNoneMatch 请求头 ETag 缓存标识，可为空
     * @return 包含分页题目数据的 HTTP 响应实体
     */
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

    /**
     * 客户端免登录浏览已发布题目的详情信息（含附件下载直链）。
     *
     * @param id          目标题目 ID，不能为 null
     * @param ifNoneMatch 请求头 ETag 缓存标识，可为空
     * @return 包含题目详情视图的 HTTP 响应实体
     */
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

    /**
     * 根据条件在已发布题目库中随机抽取一道题目。
     *
     * @param query 随机筛选范围条件，不能为 null
     * @return 随机题目视图对象
     */
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
