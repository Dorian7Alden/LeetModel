package com.leetmodel.problem.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.leetmodel.common.core.exception.BusinessException;
import com.leetmodel.common.core.result.PageResult;
import com.leetmodel.problem.entity.Contest;
import com.leetmodel.problem.entity.Problem;
import com.leetmodel.problem.entity.ProblemFavorite;
import com.leetmodel.problem.enums.ProblemErrorCode;
import com.leetmodel.problem.mapper.ContestMapper;
import com.leetmodel.problem.mapper.ProblemFavoriteMapper;
import com.leetmodel.problem.mapper.ProblemMapper;
import com.leetmodel.problem.service.ProblemFavoriteService;
import com.leetmodel.problem.service.ProblemService;
import com.leetmodel.problem.vo.ProblemFavoriteRecordVO;
import com.leetmodel.problem.vo.ProblemVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 题目收藏业务实现类。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProblemFavoriteServiceImpl implements ProblemFavoriteService {

    private final ProblemFavoriteMapper problemFavoriteMapper;
    private final ProblemMapper problemMapper;
    private final ContestMapper contestMapper;
    private final ProblemService problemService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean addFavorite(Long userId, Long problemId) {
        if (userId == null || problemId == null) {
            return false;
        }

        Problem problem = problemMapper.selectById(problemId);
        BusinessException.throwIf(
                problem == null || !Integer.valueOf(1).equals(problem.getStatus()),
                ProblemErrorCode.PROBLEM_NOT_FOUND
        );

        ProblemFavorite existing = problemFavoriteMapper.selectOne(
                new LambdaQueryWrapper<ProblemFavorite>()
                        .eq(ProblemFavorite::getUserId, userId)
                        .eq(ProblemFavorite::getProblemId, problemId)
                        .last("LIMIT 1")
        );
        if (existing != null) {
            return true;
        }

        ProblemFavorite favorite = ProblemFavorite.builder()
                .userId(userId)
                .problemId(problemId)
                .createTime(LocalDateTime.now())
                .build();
        try {
            problemFavoriteMapper.insert(favorite);
        } catch (DuplicateKeyException e) {
            log.info("用户 [{}] 重复收藏题目 [{}]，幂等忽略", userId, problemId);
        }
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeFavorite(Long userId, Long problemId) {
        if (userId == null || problemId == null) {
            return false;
        }
        problemFavoriteMapper.delete(
                new LambdaQueryWrapper<ProblemFavorite>()
                        .eq(ProblemFavorite::getUserId, userId)
                        .eq(ProblemFavorite::getProblemId, problemId)
        );
        return true;
    }

    @Override
    public boolean isFavorited(Long userId, Long problemId) {
        if (userId == null || problemId == null) {
            return false;
        }
        Long count = problemFavoriteMapper.selectCount(
                new LambdaQueryWrapper<ProblemFavorite>()
                        .eq(ProblemFavorite::getUserId, userId)
                        .eq(ProblemFavorite::getProblemId, problemId)
        );
        return count != null && count > 0;
    }

    @Override
    public List<ProblemFavoriteRecordVO> listFavoriteRecords(Long userId) {
        if (userId == null) {
            return Collections.emptyList();
        }
        List<ProblemFavorite> list = problemFavoriteMapper.selectList(
                new LambdaQueryWrapper<ProblemFavorite>()
                        .eq(ProblemFavorite::getUserId, userId)
                        .orderByAsc(ProblemFavorite::getCreateTime)
        );
        return list.stream()
                .map(fav -> ProblemFavoriteRecordVO.builder()
                        .problemId(fav.getProblemId())
                        .favoritedAt(fav.getCreateTime() == null ? null :
                                fav.getCreateTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
                        .createTime(fav.getCreateTime())
                        .build())
                .toList();
    }

    @Override
    public PageResult<ProblemVO> pageFavoriteProblems(Long userId, int page, int pageSize) {
        int pageNum = Math.max(1, page);
        int sizeNum = Math.max(1, pageSize);
        if (userId == null) {
            return new PageResult<>(0L, pageNum, sizeNum, Collections.emptyList());
        }

        Page<ProblemFavorite> pageParam = new Page<>(pageNum, sizeNum);
        Page<ProblemFavorite> favPage = problemFavoriteMapper.selectPage(
                pageParam,
                new LambdaQueryWrapper<ProblemFavorite>()
                        .eq(ProblemFavorite::getUserId, userId)
                        .orderByAsc(ProblemFavorite::getCreateTime)
        );

        List<ProblemFavorite> records = favPage.getRecords();
        if (records == null || records.isEmpty()) {
            return new PageResult<>(favPage.getTotal(), pageNum, sizeNum, Collections.emptyList());
        }

        List<Long> problemIds = records.stream()
                .map(ProblemFavorite::getProblemId)
                .filter(Objects::nonNull)
                .toList();
        if (problemIds.isEmpty()) {
            return new PageResult<>(favPage.getTotal(), pageNum, sizeNum, Collections.emptyList());
        }

        List<Problem> problems = problemMapper.selectBatchIds(problemIds);
        Map<Long, Problem> problemMap = problems.stream()
                .collect(Collectors.toMap(Problem::getId, Function.identity(), (p1, p2) -> p1));

        List<Long> contestIds = problems.stream()
                .map(Problem::getContestId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, Contest> contestMap = contestIds.isEmpty() ? Collections.emptyMap() :
                contestMapper.selectBatchIds(contestIds).stream()
                        .collect(Collectors.toMap(Contest::getId, Function.identity(), (c1, c2) -> c1));

        List<ProblemVO> voList = new ArrayList<>();
        for (ProblemFavorite fav : records) {
            Problem p = problemMap.get(fav.getProblemId());
            if (p == null) continue;
            Contest contest = contestMap.get(p.getContestId());
            List<String> tagNames = problemService.getTagNames(p.getId());
            voList.add(toVO(p, tagNames, contest));
        }

        return new PageResult<>(favPage.getTotal(), pageNum, sizeNum, voList);
    }

    private ProblemVO toVO(Problem p, List<String> tagNames, Contest contest) {
        return ProblemVO.builder()
                .id(p.getId())
                .code(p.getCode())
                .problemNumber(p.getProblemNumber())
                .title(p.getTitle())
                .contestId(p.getContestId())
                .contestCode(contest == null ? null : contest.getCode())
                .contestName(contest == null ? null : contest.getName())
                .year(p.getYear())
                .statementLanguage(p.getStatementLanguage())
                .durationMinutes(p.getDurationMinutes())
                .difficulty(p.getDifficulty())
                .averageScore(p.getAverageScore())
                .status(p.getStatus())
                .creatorId(p.getCreatorId())
                .createTime(p.getCreateTime())
                .updateTime(p.getUpdateTime())
                .tagNames(tagNames)
                .build();
    }
}
