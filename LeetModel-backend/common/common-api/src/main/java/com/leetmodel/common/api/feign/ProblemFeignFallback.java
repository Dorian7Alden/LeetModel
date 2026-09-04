package com.leetmodel.common.api.feign;

import com.leetmodel.common.api.dto.ProblemPracticeDTO;
import com.leetmodel.common.api.dto.ProblemOptionDTO;
import com.leetmodel.common.api.dto.ProblemContextDTO;
import com.leetmodel.common.api.dto.AssistantProblemQueryDTO;
import com.leetmodel.common.api.dto.AssistantProblemResultDTO;
import com.leetmodel.common.core.exception.ErrorCodeEnum;
import com.leetmodel.common.core.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 题目微服务 Feign 客户端降级工厂。
 *
 * <p>当 problem-service 发生超时或网络异常时触发降级，统一返回系统错误响应。</p>
 */
@Slf4j
@Component
public class ProblemFeignFallback implements FallbackFactory<ProblemFeignClient> {

    /**
     * 创建 ProblemFeignClient 失败降级代理实例。
     *
     * @param cause 触发远程调用失败的底层异常对象
     * @return 返回统一错误码的降级客户端实例
     */
    @Override
    public ProblemFeignClient create(Throwable cause) {
        log.error("ProblemFeignClient 调用失败", cause);
        return new ProblemFeignClient() {
            @Override
            public Result<Long> getProblemCount() {
                return Result.fail(ErrorCodeEnum.SYSTEM_ERROR);
            }

            @Override
            public Result<ProblemPracticeDTO> getPracticeProblem(Long problemId) {
                return Result.fail(ErrorCodeEnum.SYSTEM_ERROR);
            }

            @Override
            public Result<ProblemContextDTO> getProblemContext(Long problemId) {
                return Result.fail(ErrorCodeEnum.SYSTEM_ERROR);
            }

            @Override
            public Result<List<ProblemPracticeDTO>> getPracticeProblems(List<Long> problemIds) {
                return Result.fail(ErrorCodeEnum.SYSTEM_ERROR);
            }

            @Override
            public Result<List<ProblemOptionDTO>> getPublishedOptions(String keyword, Integer limit) {
                return Result.fail(ErrorCodeEnum.SYSTEM_ERROR);
            }

            @Override
            public Result<AssistantProblemResultDTO> queryForAssistant(AssistantProblemQueryDTO request) {
                return Result.fail(ErrorCodeEnum.SYSTEM_ERROR);
            }
        };
    }
}
