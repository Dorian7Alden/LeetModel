package com.leetmodel.evaluation.controller;

import com.leetmodel.common.api.dto.EvaluationComparisonDTO;
import com.leetmodel.common.api.dto.EvaluationDatasetCreateDTO;
import com.leetmodel.common.api.dto.EvaluationDatasetDTO;
import com.leetmodel.common.api.dto.EvaluationEstimateDTO;
import com.leetmodel.common.api.dto.EvaluationEstimateRequestDTO;
import com.leetmodel.common.api.dto.EvaluationTaskCreateDTO;
import com.leetmodel.common.api.dto.EvaluationTaskControlDTO;
import com.leetmodel.common.api.dto.EvaluationTaskDTO;
import com.leetmodel.common.api.dto.EvaluationTaskSummaryDTO;
import com.leetmodel.common.api.dto.EvaluationWeightSchemeCreateDTO;
import com.leetmodel.common.api.dto.EvaluationWeightSchemeDTO;
import com.leetmodel.common.api.dto.EvaluationScoreRecalculateDTO;
import com.leetmodel.common.api.dto.EvaluationScoreResultDTO;
import com.leetmodel.common.core.result.Result;
import com.leetmodel.evaluation.service.EvaluationService;
import com.leetmodel.evaluation.service.EvaluationEstimateService;
import com.leetmodel.evaluation.service.EvaluationWeightSchemeService;
import com.leetmodel.evaluation.service.EvaluationScoreRecalculationService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequestMapping("/internal/evaluations")
@RequiredArgsConstructor
public class InternalEvaluationController {

    private final EvaluationService evaluationService;
    private final EvaluationEstimateService estimateService;
    private final EvaluationWeightSchemeService weightSchemeService;
    private final EvaluationScoreRecalculationService scoreRecalculationService;

    /**
     * 创建用于版本离线评测的固定样本测试数据集。
     *
     * @param request 数据集创建请求对象，不能为 null
     * @return 数据集明细 DTO
     */
    @Operation(summary = "创建固定评价数据集")
    @PostMapping("/datasets")
    public Result<EvaluationDatasetDTO> createDataset(
            @Valid @RequestBody EvaluationDatasetCreateDTO request) {
        return Result.ok(evaluationService.createDataset(request));
    }

    /**
     * 查询系统全部已注册的固定评测基准数据集。
     *
     * @return 评测数据集列表
     */
    @Operation(summary = "查询固定评价数据集")
    @GetMapping("/datasets")
    public Result<List<EvaluationDatasetDTO>> listDatasets() {
        return Result.ok(evaluationService.listDatasets());
    }

    /**
     * 根据待测版本、样本规模与重复轮次预估所需 Token 总量与耗时。
     *
     * @param request 评测预估参数对象，不能为 null
     * @return 预估开销结果 DTO
     */
    @Operation(summary = "预估评价批次规模和调用量")
    @PostMapping("/estimates")
    public Result<EvaluationEstimateDTO> estimate(
            @Valid @RequestBody EvaluationEstimateRequestDTO request) {
        return Result.ok(estimateService.estimate(request));
    }

    /**
     * 创建并异步启动新的 AI 版本离线质量对比评测任务。
     *
     * @param request 包含候选版本、数据集 ID 与重复轮次的请求对象，不能为 null
     * @return 初始化的评测任务 DTO
     */
    @Operation(summary = "创建版本质量评价任务")
    @PostMapping("/tasks")
    public Result<EvaluationTaskDTO> createTask(
            @Valid @RequestBody EvaluationTaskCreateDTO request) {
        return Result.ok(evaluationService.createTask(request));
    }

    /**
     * 查询指定评测任务的当前执行进度、分项指标与得分矩阵。
     *
     * @param taskId 目标评测任务 ID，不能为 null
     * @return 评测任务详情 DTO
     */
    @Operation(summary = "查询版本质量评价任务")
    @GetMapping("/tasks/{taskId}")
    public Result<EvaluationTaskDTO> getTask(
            @PathVariable @Positive(message = "评价任务标识必须为正整数") Long taskId) {
        return Result.ok(evaluationService.getTask(taskId));
    }

    /**
     * 触发对因网络或环境偶发故障而失败的评测任务的恢复重试。
     *
     * @param taskId 目标评测任务 ID，不能为 null
     * @return 重试后的任务详情 DTO
     */
    @Operation(summary = "重试环境失败的版本质量评价任务")
    @PostMapping("/tasks/{taskId}/retry")
    public Result<EvaluationTaskDTO> retry(
            @PathVariable @Positive(message = "评价任务标识必须为正整数") Long taskId) {
        return Result.ok(evaluationService.retry(taskId));
    }

    /**
     * 暂停进行中评测任务的新槽位调度。
     *
     * @param taskId  目标评测任务 ID，不能为 null
     * @param request 控制参数对象，不能为 null
     * @return 更新后的任务详情 DTO
     */
    @Operation(summary = "暂停评价任务的新槽位派发")
    @PostMapping("/tasks/{taskId}/pause")
    public Result<EvaluationTaskDTO> pause(@PathVariable @Positive Long taskId,
                                           @Valid @RequestBody EvaluationTaskControlDTO request) {
        return Result.ok(evaluationService.pause(taskId, request.getOperatorId()));
    }

    /**
     * 恢复处于暂停状态评测任务的剩余槽位调度。
     *
     * @param taskId  目标评测任务 ID，不能为 null
     * @param request 控制参数对象，不能为 null
     * @return 更新后的任务详情 DTO
     */
    @Operation(summary = "恢复评价任务剩余槽位")
    @PostMapping("/tasks/{taskId}/resume")
    public Result<EvaluationTaskDTO> resume(@PathVariable @Positive Long taskId,
                                            @Valid @RequestBody EvaluationTaskControlDTO request) {
        return Result.ok(evaluationService.resume(taskId, request.getOperatorId()));
    }

    /**
     * 取消指定评测任务并清理在排队中的 AI 调用。
     *
     * @param taskId  目标评测任务 ID，不能为 null
     * @param request 控制参数对象，不能为 null
     * @return 取消后的任务详情 DTO
     */
    @Operation(summary = "取消评价任务及可取消的排队调用")
    @PostMapping("/tasks/{taskId}/cancel")
    public Result<EvaluationTaskDTO> cancel(@PathVariable @Positive Long taskId,
                                            @Valid @RequestBody EvaluationTaskControlDTO request) {
        return Result.ok(evaluationService.cancel(taskId, request.getOperatorId()));
    }

    /**
     * 按时间倒序查询最近生成的评测任务列表。
     *
     * @param limit 单次拉取数量上限
     * @return 评测任务摘要 DTO 列表
     */
    @Operation(summary = "查询最近版本质量评价任务")
    @GetMapping("/tasks")
    public Result<List<EvaluationTaskSummaryDTO>> listRecent(
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "查询数量不能小于1")
            @Max(value = 100, message = "查询数量不能超过100") Integer limit) {
        return Result.ok(evaluationService.listRecentTasks(limit));
    }

    /**
     * 统计系统全量评测任务总数。
     *
     * @return 评测任务总数
     */
    @Operation(summary = "获取版本质量评价任务数量")
    @GetMapping("/tasks/count")
    public Result<Long> countTasks() {
        return Result.ok(evaluationService.countTasks());
    }

    /**
     * 横向对比相同数据集口径下各待测版本的得分、方差与推荐胜出指数。
     *
     * @param datasetId   评测数据集 ID，不能为 null
     * @param repeatCount 重复轮次
     * @return 评测对比分析结果 DTO
     */
    @Operation(summary = "对比相同评价口径下的评审版本")
    @GetMapping("/comparisons")
    public Result<EvaluationComparisonDTO> compare(
            @RequestParam @Positive(message = "数据集标识必须为正整数") Long datasetId,
            @RequestParam @Min(value = 1, message = "重复次数不能小于1")
            @Max(value = 100, message = "重复次数不能超过100") Integer repeatCount) {
        return Result.ok(evaluationService.compare(datasetId, repeatCount));
    }

    /**
     * 创建版本选择决策所使用的指标权重方案。
     *
     * @param request 权重方案创建对象，不能为 null
     * @return 创建后的权重方案 DTO
     */
    @Operation(summary = "创建版本化权重方案")
    @PostMapping("/weight-schemes")
    public Result<EvaluationWeightSchemeDTO> createWeightScheme(
            @Valid @RequestBody EvaluationWeightSchemeCreateDTO request) {
        return Result.ok(weightSchemeService.create(request));
    }

    /**
     * 条件查询评测指标权重分配方案列表。
     *
     * @param featureCode 可选的功能标识码
     * @param status      可选的启用状态过滤
     * @return 权重方案 DTO 列表
     */
    @Operation(summary = "查询版本化权重方案")
    @GetMapping("/weight-schemes")
    public Result<List<EvaluationWeightSchemeDTO>> listWeightSchemes(
            @RequestParam(required = false) String featureCode,
            @RequestParam(required = false) String status) {
        return Result.ok(weightSchemeService.list(featureCode, status));
    }

    /**
     * 停用指定的指标权重方案。
     *
     * @param schemeId 目标方案 ID，不能为 null
     * @param request  控制参数对象，不能为 null
     * @return 停用后的方案 DTO
     */
    @Operation(summary = "停用版本化权重方案")
    @PostMapping("/weight-schemes/{schemeId}/deactivate")
    public Result<EvaluationWeightSchemeDTO> deactivateWeightScheme(
            @PathVariable @Positive(message = "权重方案标识必须为正整数") Long schemeId,
            @Valid @RequestBody EvaluationTaskControlDTO request) {
        return Result.ok(weightSchemeService.deactivate(schemeId, request.getOperatorId()));
    }

    /**
     * 使用另一指定权重方案对已有评测任务追加重新核算选择指数。
     *
     * @param taskId  目标评测任务 ID，不能为 null
     * @param request 重算请求对象，不能为 null
     * @return 重新核算后的得分结果 DTO
     */
    @Operation(summary = "使用另一权重方案追加版本选择指数结果")
    @PostMapping("/tasks/{taskId}/score-results/recalculate")
    public Result<EvaluationScoreResultDTO> recalculateScore(
            @PathVariable @Positive(message = "评价任务标识必须为正整数") Long taskId,
            @Valid @RequestBody EvaluationScoreRecalculateDTO request) {
        return Result.ok(scoreRecalculationService.recalculate(taskId, request));
    }
}
