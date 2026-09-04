package com.leetmodel.review.controller;
import com.leetmodel.common.core.result.Result;
import com.leetmodel.common.security.context.UserContext;
import com.leetmodel.review.service.ReviewService;
import com.leetmodel.review.vo.ReviewVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController @RequestMapping("/api/reviews") @RequiredArgsConstructor @Tag(name="AI 评审")
public class ReviewController {
    private final ReviewService reviewService;

    /**
     * 查询指定评审任务的状态、进度、分项评分与评审报告。
     *
     * @param taskId 目标评审任务 ID，不能为 null
     * @return 评审任务视图对象
     */
    @Operation(summary="查询评审任务和结果") @GetMapping("/{taskId}")
    public Result<ReviewVO> get(@PathVariable Long taskId) {
        return Result.ok(reviewService.getTask(taskId, UserContext.getUserId()));
    }

    /**
     * 查询队伍名下的全部评审任务与成绩结果列表。
     *
     * @param teamId 目标队伍 ID，不能为 null
     * @return 队伍评审视图列表
     */
    @Operation(summary="查询队伍评审结果") @GetMapping("/teams/{teamId}")
    public Result<List<ReviewVO>> list(@PathVariable Long teamId) {
        return Result.ok(reviewService.listTeamResults(teamId, UserContext.getUserId()));
    }

    /**
     * 手动触发对失败评审任务的重试处理。
     *
     * @param taskId 目标评审任务 ID，不能为 null
     * @return 触发重试后的评审视图对象
     */
    @Operation(summary="重试失败的评审任务") @PostMapping("/{taskId}/retry")
    public Result<ReviewVO> retry(@PathVariable Long taskId) {
        return Result.ok(reviewService.retry(taskId, UserContext.getUserId()));
    }
}
