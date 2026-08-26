package com.leetmodel.common.api.feign;

import com.leetmodel.common.api.dto.ProblemPracticeDTO;
import com.leetmodel.common.api.dto.ProblemOptionDTO;
import com.leetmodel.common.core.exception.ErrorCodeEnum;
import com.leetmodel.common.core.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 题目服务 Feign 降级工厂。
 */
@Slf4j
@Component
public class ProblemFeignFallback implements FallbackFactory<ProblemFeignClient> {

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
            public Result<List<ProblemPracticeDTO>> getPracticeProblems(List<Long> problemIds) {
                return Result.fail(ErrorCodeEnum.SYSTEM_ERROR);
            }

            @Override
            public Result<List<ProblemOptionDTO>> getPublishedOptions(String keyword, Integer limit) {
                return Result.fail(ErrorCodeEnum.SYSTEM_ERROR);
            }
        };
    }
}
