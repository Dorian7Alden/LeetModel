package com.leetmodel.problem.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.leetmodel.common.core.exception.BusinessException;
import com.leetmodel.common.cache.CacheInvalidator;
import com.leetmodel.problem.cache.ProblemPublicCacheService;
import com.leetmodel.problem.audit.ProblemAuditEventProducer;
import com.leetmodel.problem.dto.ContestRequest;
import com.leetmodel.problem.entity.Contest;
import com.leetmodel.problem.entity.Problem;
import com.leetmodel.problem.enums.ProblemErrorCode;
import com.leetmodel.problem.mapper.ContestMapper;
import com.leetmodel.problem.mapper.ProblemMapper;
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
    private final ProblemMapper problemMapper;

    /**
     * 查询所有赛事字典数据列表（按编码升序排序）。
     *
     * @return 赛事字典实体列表
     */
    @Override
    public List<Contest> list() {
        return list(new LambdaQueryWrapper<Contest>().orderByAsc(Contest::getCode));
    }

    /**
     * 创建新赛事并失效公开缓存。
     *
     * @param request 赛事请求对象，不能为 null
     * @return 新建的赛事实体
     */
    @Override
    @Transactional
    public Contest create(ContestRequest request) {
        String normalizedCode = request.getCode().trim().toUpperCase();
        boolean duplicate = exists(new LambdaQueryWrapper<Contest>()
                .eq(Contest::getCode, normalizedCode));
        BusinessException.throwIf(duplicate, ProblemErrorCode.CONTEST_CODE_DUPLICATE);

        Contest contest = new Contest();
        contest.setCode(normalizedCode);
        contest.setName(request.getName().trim());
        contest.setEnglishName(request.getEnglishName());
        contest.setScheduleDesc(request.getScheduleDesc());
        contest.setTeamRules(request.getTeamRules());
        contest.setSubmissionSpec(request.getSubmissionSpec());
        contest.setProblemSpec(request.getProblemSpec());
        contest.setDescription(request.getDescription());
        contest.setOfficialUrl(request.getOfficialUrl());
        save(contest);

        recordPublicInvalidation();
        log.info("创建赛事完成: id={}, code={}", contest.getId(), contest.getCode());
        return contest;
    }

    /**
     * 更新指定赛事的编码与名称，并失效公开缓存与记录审计。
     *
     * @param id   目标赛事 ID，不能为 null
     * @param code 赛事编码，不能为 null
     * @param name 赛事名称，不能为 null
     * @return 更新后的赛事实体
     * @throws BusinessException 若赛事不存在或编码重复
     */
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
        recordPublicInvalidation();
        log.info("更新赛事基础数据完成: id={}", id);
        return contest;
    }

    /**
     * 更新指定赛事的完整学术档案与规格，并失效公开缓存与记录审计。
     *
     * @param id      目标赛事 ID，不能为 null
     * @param request 赛事请求对象，不能为 null
     * @return 更新后的赛事实体
     */
    @Override
    @Transactional
    public Contest update(Long id, ContestRequest request) {
        Contest contest = getById(id);
        BusinessException.throwIf(contest == null, ProblemErrorCode.CONTEST_NOT_FOUND);
        String normalizedCode = request.getCode().trim().toUpperCase();
        boolean duplicate = exists(new LambdaQueryWrapper<Contest>()
                .eq(Contest::getCode, normalizedCode)
                .ne(Contest::getId, id));
        BusinessException.throwIf(duplicate, ProblemErrorCode.CONTEST_CODE_DUPLICATE);
        contest.setCode(normalizedCode);
        contest.setName(request.getName().trim());
        contest.setEnglishName(request.getEnglishName());
        contest.setScheduleDesc(request.getScheduleDesc());
        contest.setTeamRules(request.getTeamRules());
        contest.setSubmissionSpec(request.getSubmissionSpec());
        contest.setProblemSpec(request.getProblemSpec());
        contest.setDescription(request.getDescription());
        contest.setOfficialUrl(request.getOfficialUrl());
        updateById(contest);
        audit.contestUpdated(id);
        recordPublicInvalidation();
        log.info("更新赛事完整档案完成: id={}", id);
        return contest;
    }

    /**
     * 删除指定赛事（校验是否存在以及是否被题目引用）。
     *
     * @param id 目标赛事 ID，不能为 null
     */
    @Override
    @Transactional
    public void delete(Long id) {
        Contest contest = getById(id);
        BusinessException.throwIf(contest == null, ProblemErrorCode.CONTEST_NOT_FOUND);

        boolean inUse = problemMapper.exists(new LambdaQueryWrapper<Problem>()
                .eq(Problem::getContestId, id));
        BusinessException.throwIf(inUse, ProblemErrorCode.CONTEST_IN_USE);

        removeById(id);
        recordPublicInvalidation();
        log.info("删除赛事完成: id={}", id);
    }

    private void recordPublicInvalidation() {
        cacheInvalidator.record(
                ProblemPublicCacheService.REGION,
                ProblemPublicCacheService.SCOPE,
                ProblemPublicCacheService.SCHEMA_VERSION
        );
    }
}
