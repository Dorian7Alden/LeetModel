package com.leetmodel.problem.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.leetmodel.common.core.result.PageResult;
import com.leetmodel.common.core.result.Result;
import com.leetmodel.common.security.context.UserContext;
import com.leetmodel.problem.service.ProblemFavoriteService;
import com.leetmodel.problem.vo.ProblemFavoriteRecordVO;
import com.leetmodel.problem.vo.ProblemVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 题目收藏控制器（需登录用户使用）。
 */
@RestController
@RequestMapping("/api/problems")
@RequiredArgsConstructor
@SaCheckLogin
@Tag(name = "题目收藏")
@Validated
public class ProblemFavoriteController {

    private final ProblemFavoriteService problemFavoriteService;

    @PostMapping("/{id}/favorite")
    @Operation(summary = "收藏题目", description = "将指定题目加入当前登录用户的收藏题单")
    public Result<Boolean> addFavorite(@PathVariable("id") Long problemId) {
        Long userId = UserContext.getUserId();
        return Result.ok(problemFavoriteService.addFavorite(userId, problemId));
    }

    @DeleteMapping("/{id}/favorite")
    @Operation(summary = "取消收藏题目", description = "将指定题目从当前登录用户的收藏题单中移除")
    public Result<Boolean> removeFavorite(@PathVariable("id") Long problemId) {
        Long userId = UserContext.getUserId();
        return Result.ok(problemFavoriteService.removeFavorite(userId, problemId));
    }

    @GetMapping("/{id}/favorite")
    @Operation(summary = "检查题目是否已收藏", description = "判断当前登录用户是否已收藏指定题目")
    public Result<Boolean> isFavorited(@PathVariable("id") Long problemId) {
        Long userId = UserContext.getUserId();
        return Result.ok(problemFavoriteService.isFavorited(userId, problemId));
    }

    @GetMapping("/favorites")
    @Operation(summary = "获取当前用户全部收藏记录", description = "返回所有收藏题目 ID 与时间戳，按收藏时刻正序排列（最先收藏的排在最前面）")
    public Result<List<ProblemFavoriteRecordVO>> listFavorites() {
        Long userId = UserContext.getUserId();
        return Result.ok(problemFavoriteService.listFavoriteRecords(userId));
    }

    @GetMapping("/favorites/page")
    @Operation(summary = "分页获取当前用户收藏题目列表", description = "分页返回收藏题目的完整卡片信息，按收藏时刻正序排列（最先收藏的排在最前面）")
    public Result<PageResult<ProblemVO>> pageFavorites(
            @RequestParam(value = "page", defaultValue = "1") @Min(value = 1, message = "页码最小为 1") int page,
            @RequestParam(value = "pageSize", defaultValue = "10") @Min(value = 1, message = "每页条数最小为 1") @Max(value = 100, message = "每页条数最大为 100") int pageSize
    ) {
        Long userId = UserContext.getUserId();
        return Result.ok(problemFavoriteService.pageFavoriteProblems(userId, page, pageSize));
    }
}
