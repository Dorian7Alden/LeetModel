package com.leetmodel.problem.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.leetmodel.common.core.exception.BusinessException;
import com.leetmodel.problem.dto.ProblemCreateRequest;
import com.leetmodel.problem.dto.ProblemPageQuery;
import com.leetmodel.problem.dto.ProblemUpdateRequest;
import com.leetmodel.problem.entity.Problem;
import com.leetmodel.problem.entity.Contest;
import com.leetmodel.problem.entity.ProblemLink;
import com.leetmodel.problem.entity.ProblemTag;
import com.leetmodel.problem.entity.Tag;
import com.leetmodel.problem.enums.ProblemErrorCode;
import com.leetmodel.problem.mapper.ProblemLinkMapper;
import com.leetmodel.problem.mapper.ContestMapper;
import com.leetmodel.problem.mapper.ProblemMapper;
import com.leetmodel.problem.mapper.ProblemTagMapper;
import com.leetmodel.problem.mapper.TagMapper;
import com.leetmodel.problem.service.ProblemService;
import com.leetmodel.problem.vo.ProblemVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 题目服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProblemServiceImpl extends ServiceImpl<ProblemMapper, Problem> implements ProblemService {

    private final ProblemTagMapper problemTagMapper;
    private final TagMapper tagMapper;
    private final ProblemLinkMapper problemLinkMapper;
    private final ContestMapper contestMapper;

    // ==================== 分页查询 ====================

    @Override
    public IPage<ProblemVO> pageProblems(ProblemPageQuery query) {
        LambdaQueryWrapper<Problem> wrapper = new LambdaQueryWrapper<>();
        if (query.getStatus() != null) {
            wrapper.eq(Problem::getStatus, query.getStatus());
        }
        if (query.getDifficulty() != null) {
            wrapper.eq(Problem::getDifficulty, query.getDifficulty());
        }
        if (query.getContestId() != null) wrapper.eq(Problem::getContestId, query.getContestId());
        if (query.getYear() != null) wrapper.eq(Problem::getYear, query.getYear());
        if (query.getStatementLanguage() != null) {
            wrapper.eq(Problem::getStatementLanguage, query.getStatementLanguage());
        }
        if (query.getKeyword() != null && !query.getKeyword().isBlank()) {
            wrapper.like(Problem::getTitle, query.getKeyword());
        }
        if (query.getTagId() != null) {
            wrapper.inSql(Problem::getId,
                    "SELECT problem_id FROM problem_tag WHERE tag_id = " + query.getTagId());
        }
        wrapper.orderByDesc(Problem::getCreateTime);

        Page<Problem> page = new Page<>(query.getPage(), query.getPageSize());
        IPage<Problem> problemPage = baseMapper.selectPage(page, wrapper);

        // 批量获取标签名称
        List<Long> problemIds = problemPage.getRecords().stream()
                .map(Problem::getId).toList();
        Map<Long, List<String>> tagMap = batchGetTagNames(problemIds);

        // 转换为 VO
        List<ProblemVO> voList = problemPage.getRecords().stream()
                .map(p -> toVO(p, tagMap.getOrDefault(p.getId(), List.of()), null))
                .toList();

        Page<ProblemVO> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        voPage.setRecords(voList);
        return voPage;
    }

    // ==================== 详情查询 ====================

    @Override
    public ProblemVO getProblemDetail(Long id) {
        Problem problem = getById(id);
        BusinessException.throwIf(problem == null, ProblemErrorCode.PROBLEM_NOT_FOUND);
        List<String> tagNames = getTagNames(id);
        List<ProblemLink> links = getLinks(id);
        return toVO(problem, tagNames, links);
    }

    /**
     * 查询已发布题目详情。
     * @param id 题目 ID
     * @return 已发布题目详情
     */
    @Override
    public ProblemVO getPublishedProblemDetail(Long id) {
        Problem problem = getById(id);
        BusinessException.throwIf(
                problem == null || !Integer.valueOf(1).equals(problem.getStatus()),
                ProblemErrorCode.PROBLEM_NOT_FOUND
        );
        List<String> tagNames = getTagNames(id);
        List<ProblemLink> links = getLinks(id);
        return toVO(problem, tagNames, links);
    }

    @Override
    public ProblemVO getRandomPublishedProblem(ProblemPageQuery query) {
        LambdaQueryWrapper<Problem> wrapper = new LambdaQueryWrapper<Problem>()
                .eq(Problem::getStatus, 1);
        if (query.getContestId() != null) wrapper.eq(Problem::getContestId, query.getContestId());
        if (query.getYear() != null) wrapper.eq(Problem::getYear, query.getYear());
        if (query.getStatementLanguage() != null) {
            wrapper.eq(Problem::getStatementLanguage, query.getStatementLanguage());
        }
        if (query.getDifficulty() != null) wrapper.eq(Problem::getDifficulty, query.getDifficulty());
        wrapper.last("ORDER BY RAND() LIMIT 1");
        Problem problem = baseMapper.selectOne(wrapper);
        BusinessException.throwIf(problem == null, ProblemErrorCode.PROBLEM_NOT_FOUND);
        return toVO(problem, getTagNames(problem.getId()), getLinks(problem.getId()));
    }

    // ==================== 创建 ====================

    @Override
    @Transactional
    public ProblemVO createProblem(ProblemCreateRequest request, Long creatorId) {
        validateContest(request.getContestId());

        Problem problem = new Problem();
        problem.setTitle(request.getTitle());
        problem.setContentFileId(request.getContentFileId());
        problem.setContestId(request.getContestId());
        problem.setYear(request.getYear());
        problem.setStatementLanguage(request.getStatementLanguage());
        problem.setDurationMinutes(request.getDurationMinutes());
        problem.setDifficulty(request.getDifficulty());
        problem.setStatus(request.getStatus() != null ? request.getStatus() : 0);
        problem.setAverageScore(BigDecimal.ZERO);
        problem.setCreatorId(creatorId);

        save(problem);
        log.info("创建题目: {} [ID: {}]", problem.getTitle(), problem.getId());

        // 保存标签和链接
        List<String> tagNames = saveTags(problem.getId(), request.getTagIds());
        List<ProblemLink> links = saveLinks(problem.getId(),
                request.getLinks() != null ? request.getLinks().stream()
                        .map(l -> new ProblemLink.LinkData(l.getTitle(), l.getUrl(),
                                l.getDescription(), l.getSortOrder()))
                        .toList() : List.of());

        return toVO(problem, tagNames, links);
    }

    // ==================== 更新 ====================

    @Override
    @Transactional
    public ProblemVO updateProblem(Long id, ProblemUpdateRequest request) {
        Problem problem = getById(id);
        BusinessException.throwIf(problem == null, ProblemErrorCode.PROBLEM_NOT_FOUND);

        if (request.getContestId() != null) validateContest(request.getContestId());

        boolean changed = false;
        if (request.getTitle() != null && !request.getTitle().isBlank()) {
            problem.setTitle(request.getTitle());
            changed = true;
        }
        if (request.getContentFileId() != null) {
            problem.setContentFileId(request.getContentFileId());
            changed = true;
        }
        if (request.getContestId() != null) {
            problem.setContestId(request.getContestId());
            changed = true;
        }
        if (request.getYear() != null) { problem.setYear(request.getYear()); changed = true; }
        if (request.getStatementLanguage() != null) {
            problem.setStatementLanguage(request.getStatementLanguage()); changed = true;
        }
        if (request.getDurationMinutes() != null) {
            problem.setDurationMinutes(request.getDurationMinutes()); changed = true;
        }
        if (request.getDifficulty() != null) {
            problem.setDifficulty(request.getDifficulty());
            changed = true;
        }
        if (request.getStatus() != null) {
            problem.setStatus(request.getStatus());
            changed = true;
        }
        if (changed) {
            updateById(problem);
        }

        // 标签和链接：null 不修改，非 null 替换
        List<String> tagNames = getTagNames(id);
        if (request.getTagIds() != null) {
            tagNames = replaceTags(id, request.getTagIds());
        }

        List<ProblemLink> links = getLinks(id);
        if (request.getLinks() != null) {
            links = replaceLinks(id, request.getLinks().stream()
                    .map(l -> new ProblemLink.LinkData(l.getTitle(), l.getUrl(),
                            l.getDescription(), l.getSortOrder()))
                    .toList());
        }

        log.info("更新题目: {}", id);
        return toVO(problem, tagNames, links);
    }

    /**
     * 删除题目及其标签、链接关系。
     * @param id 题目 ID
     */
    @Override
    @Transactional
    public void deleteProblem(Long id) {
        // 校验题目存在
        Problem problem = getById(id);
        BusinessException.throwIf(problem == null, ProblemErrorCode.PROBLEM_NOT_FOUND);

        // 删除题目关联数据
        LambdaQueryWrapper<ProblemTag> tagWrapper = new LambdaQueryWrapper<>();
        tagWrapper.eq(ProblemTag::getProblemId, id);
        problemTagMapper.delete(tagWrapper);

        LambdaQueryWrapper<ProblemLink> linkWrapper = new LambdaQueryWrapper<>();
        linkWrapper.eq(ProblemLink::getProblemId, id);
        problemLinkMapper.delete(linkWrapper);

        // 逻辑删除题目
        removeById(id);
        log.info("删除题目: {}", id);
    }

    // ==================== 标签名称查询 ====================

    @Override
    public List<String> getTagNames(Long problemId) {
        LambdaQueryWrapper<ProblemTag> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProblemTag::getProblemId, problemId);
        List<Long> tagIds = problemTagMapper.selectList(wrapper).stream()
                .map(ProblemTag::getTagId).toList();
        if (tagIds.isEmpty()) {
            return List.of();
        }
        return tagMapper.selectBatchIds(tagIds).stream()
                .map(Tag::getName).toList();
    }

    // ==================== 私有方法 ====================

    /**
     * 批量获取题目 ID 对应的标签名称 Map。
     */
    private Map<Long, List<String>> batchGetTagNames(List<Long> problemIds) {
        if (problemIds.isEmpty()) {
            return Map.of();
        }

        LambdaQueryWrapper<ProblemTag> ptWrapper = new LambdaQueryWrapper<>();
        ptWrapper.in(ProblemTag::getProblemId, problemIds);
        List<ProblemTag> ptList = problemTagMapper.selectList(ptWrapper);

        if (ptList.isEmpty()) {
            Map<Long, List<String>> empty = new HashMap<>();
            problemIds.forEach(id -> empty.put(id, List.of()));
            return empty;
        }

        List<Long> tagIds = ptList.stream().map(ProblemTag::getTagId).distinct().toList();
        List<Tag> tags = tagMapper.selectBatchIds(tagIds);
        Map<Long, String> idToName = tags.stream()
                .collect(Collectors.toMap(Tag::getId, Tag::getName));

        Map<Long, List<String>> result = new HashMap<>();
        for (ProblemTag pt : ptList) {
            result.computeIfAbsent(pt.getProblemId(), k -> new ArrayList<>())
                    .add(idToName.get(pt.getTagId()));
        }
        problemIds.forEach(id -> result.putIfAbsent(id, List.of()));
        return result;
    }

    /**
     * 保存题目标签关联，返回标签名称列表。
     */
    private List<String> saveTags(Long problemId, List<Long> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) return List.of();

        // 去重并校验标签全部存在
        List<Long> uniqueTagIds = new ArrayList<>(new LinkedHashSet<>(tagIds));
        List<Tag> tags = tagMapper.selectBatchIds(uniqueTagIds);
        BusinessException.throwIf(
                tags.size() != uniqueTagIds.size(),
                ProblemErrorCode.TAG_NOT_FOUND
        );

        // 保存题目标签关系
        for (Long tagId : uniqueTagIds) {
            ProblemTag pt = new ProblemTag();
            pt.setProblemId(problemId);
            pt.setTagId(tagId);
            problemTagMapper.insert(pt);
        }
        return tags.stream().map(Tag::getName).toList();
    }

    /**
     * 替换题目标签关联（先删后插），返回新的标签名称列表。
     */
    private List<String> replaceTags(Long problemId, List<Long> tagIds) {
        LambdaQueryWrapper<ProblemTag> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProblemTag::getProblemId, problemId);
        problemTagMapper.delete(wrapper);
        return saveTags(problemId, tagIds);
    }

    /**
     * 保存外部链接，返回链接列表。
     */
    private List<ProblemLink> saveLinks(Long problemId, List<ProblemLink.LinkData> links) {
        if (links == null || links.isEmpty()) {
            return List.of();
        }
        List<ProblemLink> result = new ArrayList<>();
        for (ProblemLink.LinkData link : links) {
            ProblemLink pl = new ProblemLink();
            pl.setProblemId(problemId);
            pl.setTitle(link.title());
            pl.setUrl(link.url());
            pl.setDescription(link.description());
            pl.setSortOrder(link.sortOrder() != null ? link.sortOrder() : 0);
            problemLinkMapper.insert(pl);
            result.add(pl);
        }
        return result;
    }

    /**
     * 替换外部链接（先删后插），返回新的链接列表。
     */
    private List<ProblemLink> replaceLinks(Long problemId, List<ProblemLink.LinkData> links) {
        LambdaQueryWrapper<ProblemLink> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProblemLink::getProblemId, problemId);
        problemLinkMapper.delete(wrapper);
        return saveLinks(problemId, links);
    }

    /**
     * 获取题目的外部链接列表。
     */
    private List<ProblemLink> getLinks(Long problemId) {
        LambdaQueryWrapper<ProblemLink> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProblemLink::getProblemId, problemId);
        wrapper.orderByAsc(ProblemLink::getSortOrder);
        return problemLinkMapper.selectList(wrapper);
    }

    /**
     * 校验赛事类型合法性。
     */
    private void validateContest(Long contestId) {
        Contest contest = contestMapper.selectById(contestId);
        BusinessException.throwIf(contest == null || !Integer.valueOf(1).equals(contest.getStatus()),
                ProblemErrorCode.CONTEST_NOT_FOUND);
    }

    /**
     * Problem 实体转 ProblemVO。
     */
    private ProblemVO toVO(Problem p, List<String> tagNames, List<ProblemLink> links) {
        Contest contest = contestMapper.selectById(p.getContestId());
        ProblemVO.ProblemVOBuilder builder = ProblemVO.builder()
                .id(p.getId())
                .title(p.getTitle())
                .contentFileId(p.getContentFileId())
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
                .tagNames(tagNames);

        if (links != null) {
            builder.links(links.stream()
                    .map(l -> ProblemVO.LinkVO.builder()
                            .id(l.getId())
                            .title(l.getTitle())
                            .url(l.getUrl())
                            .description(l.getDescription())
                            .sortOrder(l.getSortOrder())
                            .build())
                    .toList());
        }

        return builder.build();
    }
}
