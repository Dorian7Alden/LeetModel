package com.leetmodel.problem.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.leetmodel.common.core.exception.BusinessException;
import com.leetmodel.common.cache.CacheInvalidator;
import com.leetmodel.problem.cache.ProblemPublicCacheService;
import com.leetmodel.problem.audit.ProblemAuditEventProducer;
import com.leetmodel.problem.entity.Contest;
import com.leetmodel.problem.enums.ProblemErrorCode;
import com.leetmodel.problem.mapper.ContestMapper;
import com.leetmodel.problem.service.ContestService;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 赛事基础数据服务实现。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContestServiceImpl extends ServiceImpl<ContestMapper, Contest> implements ContestService {

    private final CacheInvalidator cacheInvalidator;
    private final ProblemAuditEventProducer audit;

    @Override
    public List<Contest> list() {
        return list(new LambdaQueryWrapper<Contest>().orderByAsc(Contest::getCode));
    }

    @Override
    @Transactional
    public Contest update(Long id, String code, String name) {
        Contest contest = getById(id);
        BusinessException.throwIf(contest == null, ProblemErrorCode.CONTEST_NOT_FOUND);
        String normalizedCode = code.trim().toUpperCase();
        boolean duplicate = exists(new LambdaQueryWrapper<Contest>()
                .eq(Contest::getCode, normalizedCode)
                .ne(Contest::getId, id));
        BusinessException.throwIf(duplicate, ProblemErrorCode.CONTEST_CODE_DUPLICATE);
        contest.setCode(normalizedCode);
        contest.setName(name.trim());
        updateById(contest);
        audit.contestUpdated(id);
        cacheInvalidator.record(
                ProblemPublicCacheService.REGION,
                ProblemPublicCacheService.SCOPE,
                ProblemPublicCacheService.SCHEMA_VERSION
        );
        log.info("更新赛事基础数据完成: id={}", id);
        return contest;
    }
}
