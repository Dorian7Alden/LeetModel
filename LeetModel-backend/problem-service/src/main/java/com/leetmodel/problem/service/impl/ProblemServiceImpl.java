package com.leetmodel.problem.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.leetmodel.common.core.exception.BusinessException;
import com.leetmodel.common.core.exception.ErrorCodeEnum;
import com.leetmodel.common.core.result.Result;
import com.leetmodel.common.cache.CacheInvalidator;
import com.leetmodel.common.api.dto.AssistantProblemQueryDTO;
import com.leetmodel.common.api.dto.AssistantProblemQueryMode;
import com.leetmodel.common.api.dto.AssistantProblemResultDTO;
import com.leetmodel.common.api.dto.FileAccessUrlDTO;
import com.leetmodel.common.api.dto.FileAssetSummaryDTO;
import com.leetmodel.common.api.feign.FileFeignClient;
import com.leetmodel.problem.dto.ProblemCreateRequest;
import com.leetmodel.problem.cache.ProblemDetailReadModel;
import com.leetmodel.problem.audit.ProblemAuditEventProducer;
import com.leetmodel.problem.cache.ProblemPublicCacheService;
import com.leetmodel.problem.dto.ProblemPageQuery;
import com.leetmodel.problem.dto.ProblemUpdateRequest;
import com.leetmodel.problem.entity.Problem;
import com.leetmodel.problem.entity.Contest;
import com.leetmodel.problem.entity.ProblemAttachment;
import com.leetmodel.problem.entity.ProblemTag;
import com.leetmodel.problem.entity.Tag;
import com.leetmodel.problem.enums.ProblemErrorCode;
import com.leetmodel.problem.enums.TagType;
import com.leetmodel.problem.mapper.ProblemAttachmentMapper;
import com.leetmodel.problem.mapper.ContestMapper;
import com.leetmodel.problem.mapper.ProblemMapper;
import com.leetmodel.problem.mapper.ProblemTagMapper;
import com.leetmodel.problem.mapper.TagMapper;
import com.leetmodel.problem.messaging.ProblemAttachmentEventProducer;
import com.leetmodel.problem.service.ProblemService;
import com.leetmodel.problem.vo.ProblemVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

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

    /** 统一响应体成功状态码。 */
    private static final int RESULT_SUCCESS_CODE = 20000;

    private final ProblemTagMapper problemTagMapper;
    private final TagMapper tagMapper;
    private final ProblemAttachmentMapper problemAttachmentMapper;
    private final ContestMapper contestMapper;
    private final FileFeignClient fileFeignClient;
    private final ProblemAttachmentEventProducer attachmentEvents;
    private final CacheInvalidator cacheInvalidator;
    private final ProblemAuditEventProducer audit;

    // ==================== 分页查询 ====================

    /**
     * 管理员分页组合条件查询题目列表（含标签与赛事信息）。
     *
     * @param query 分页与组合筛选条件对象，不能为 null
     * @return 分页包装的题目视图列表
     */
    @Override
    public IPage<ProblemVO> pageProblems(ProblemPageQuery query) {
        validateScoreRange(query);
        LambdaQueryWrapper<Problem> wrapper = new LambdaQueryWrapper<>();
        if (query.getStatus() != null) {
            wrapper.eq(Problem::getStatus, query.getStatus());
        }
        if (query.getDifficulty() != null) {
            wrapper.eq(Problem::getDifficulty, query.getDifficulty());
        }
        if (query.getContestId() != null) wrapper.eq(Problem::getContestId, query.getContestId());
        if (query.getProblemNumber() != null) {
            wrapper.eq(Problem::getProblemNumber, query.getProblemNumber());
        }
        if (query.getYear() != null) wrapper.eq(Problem::getYear, query.getYear());
        if (query.getStatementLanguage() != null) {
            wrapper.eq(Problem::getStatementLanguage, query.getStatementLanguage());
        }
        if (query.getMinAverageScore() != null) {
            wrapper.ge(Problem::getAverageScore, query.getMinAverageScore());
        }
        if (query.getMaxAverageScore() != null) {
            wrapper.le(Problem::getAverageScore, query.getMaxAverageScore());
        }
        if (query.getKeyword() != null && !query.getKeyword().isBlank()) {
            wrapper.like(Problem::getTitle, query.getKeyword());
        }
        List<Tag> filterTags = validateTags(query.getTagIds());
        for (Tag tag : filterTags) {
            wrapper.inSql(Problem::getId,
                    "SELECT problem_id FROM problem_tag WHERE tag_id = " + tag.getId());
        }
        applySort(wrapper, query);

        Page<Problem> page = new Page<>(query.getPage(), query.getPageSize());
        IPage<Problem> problemPage = baseMapper.selectPage(page, wrapper);

        // 批量获取标签名称
        List<Long> problemIds = problemPage.getRecords().stream()
                .map(Problem::getId).toList();
        Map<Long, List<String>> tagMap = batchGetTagNames(problemIds);
        Map<Long, Contest> contestMap = batchGetContests(problemPage.getRecords());

        // 转换为 VO
        List<ProblemVO> voList = problemPage.getRecords().stream()
                .map(p -> toVO(
                        p,
                        tagMap.getOrDefault(p.getId(), List.of()),
                        null,
                        contestMap.get(p.getContestId())
                ))
                .toList();

        Page<ProblemVO> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        voPage.setRecords(voList);
        return voPage;
    }

    // ==================== 详情查询 ====================

    /**
     * 查询题目的完整明细信息（含未发布题目与所有附件）。
     *
     * @param id 目标题目 ID，不能为 null
     * @return 题目详情视图对象
     * @throws BusinessException 若题目不存在
     */
    @Override
    public ProblemVO getProblemDetail(Long id) {
        Problem problem = getById(id);
        BusinessException.throwIf(problem == null, ProblemErrorCode.PROBLEM_NOT_FOUND);
        List<String> tagNames = getTagNames(id);
        List<ProblemAttachment> attachments = getAttachments(id);
        return toVO(problem, tagNames, attachments);
    }

    /**
     * 查询已发布题目详情。
     * @param id 题目 ID
     * @return 已发布题目详情
     */
    @Override
    public ProblemVO getPublishedProblemDetail(Long id) {
        ProblemDetailReadModel readModel = findPublishedProblemReadModel(id);
        BusinessException.throwIf(readModel == null, ProblemErrorCode.PROBLEM_NOT_FOUND);
        return materializePublishedProblem(readModel);
    }

    /**
     * 查找不含预签名 URL 的已发布题目读模型。
     *
     * @param id 题目 ID
     * @return 稳定读模型；不存在时为 null
     */
    @Override
    public ProblemDetailReadModel findPublishedProblemReadModel(Long id) {
        Problem problem = getById(id);
        if (problem == null && id != null) {
            problem = getOne(new LambdaQueryWrapper<Problem>()
                    .eq(Problem::getCode, id)
                    .eq(Problem::getStatus, 1)
                    .last("LIMIT 1"));
        }
        if (problem == null || !Integer.valueOf(1).equals(problem.getStatus())) return null;
        List<String> tagNames = getTagNames(problem.getId());
        List<ProblemAttachment> attachments = getAttachments(problem.getId());
        ProblemVO stableProblem = toVO(problem, tagNames, List.of());
        List<ProblemDetailReadModel.AttachmentReadModel> stableAttachments = attachments.stream()
                .map(attachment -> new ProblemDetailReadModel.AttachmentReadModel(
                        attachment.getId(),
                        attachment.getFileName(),
                        attachment.getFileId(),
                        attachment.getContentType(),
                        attachment.getFileSize(),
                        attachment.getDescription(),
                        attachment.getSortOrder()
                ))
                .toList();
        return new ProblemDetailReadModel(stableProblem, stableAttachments);
    }

    /**
     * 为稳定题目读模型生成当前附件下载 URL。
     *
     * @param readModel 稳定读模型
     * @return 公开题目响应
     */
    @Override
    public ProblemVO materializePublishedProblem(ProblemDetailReadModel readModel) {
        ProblemVO source = readModel.getProblem();
        List<ProblemVO.AttachmentVO> attachments = readModel.getAttachments().stream()
                .map(attachment -> ProblemVO.AttachmentVO.builder()
                        .id(attachment.getId())
                        .fileName(attachment.getFileName())
                        .contentType(attachment.getContentType())
                        .fileSize(attachment.getFileSize())
                        .description(attachment.getDescription())
                        .sortOrder(attachment.getSortOrder())
                        .downloadUrl(resolveDownloadUrl(attachment.getFileId()))
                        .build())
                .toList();
        return ProblemVO.builder()
                .id(source.getId())
                .code(source.getCode())
                .problemNumber(source.getProblemNumber())
                .title(source.getTitle())
                .contentMarkdown(source.getContentMarkdown())
                .solutionHint(source.getSolutionHint())
                .contestId(source.getContestId())
                .contestCode(source.getContestCode())
                .contestName(source.getContestName())
                .year(source.getYear())
                .statementLanguage(source.getStatementLanguage())
                .durationMinutes(source.getDurationMinutes())
                .difficulty(source.getDifficulty())
                .averageScore(source.getAverageScore())
                .status(source.getStatus())
                .creatorId(source.getCreatorId())
                .createTime(source.getCreateTime())
                .updateTime(source.getUpdateTime())
                .tagNames(source.getTagNames())
                .tags(source.getTags())
                .attachments(attachments)
                .build();
    }

    @Override
    /**
     * 根据条件在已发布的题目中随机抽取一道题目。
     *
     * @param query 过滤条件，不能为 null
     * @return 随机匹配的题目视图对象
     * @throws BusinessException 若未匹配到符合条件的已发布题目
     */
    public ProblemVO getRandomPublishedProblem(ProblemPageQuery query) {
        validateScoreRange(query);
        LambdaQueryWrapper<Problem> wrapper = new LambdaQueryWrapper<Problem>()
                .eq(Problem::getStatus, 1);
        if (query.getContestId() != null) wrapper.eq(Problem::getContestId, query.getContestId());
        if (query.getProblemNumber() != null) {
            wrapper.eq(Problem::getProblemNumber, query.getProblemNumber());
        }
        if (query.getYear() != null) wrapper.eq(Problem::getYear, query.getYear());
        if (query.getStatementLanguage() != null) {
            wrapper.eq(Problem::getStatementLanguage, query.getStatementLanguage());
        }
        if (query.getDifficulty() != null) wrapper.eq(Problem::getDifficulty, query.getDifficulty());
        if (query.getMinAverageScore() != null) {
            wrapper.ge(Problem::getAverageScore, query.getMinAverageScore());
        }
        if (query.getMaxAverageScore() != null) {
            wrapper.le(Problem::getAverageScore, query.getMaxAverageScore());
        }
        if (query.getKeyword() != null && !query.getKeyword().isBlank()) {
            wrapper.like(Problem::getTitle, query.getKeyword());
        }
        for (Tag tag : validateTags(query.getTagIds())) {
            wrapper.inSql(Problem::getId,
                    "SELECT problem_id FROM problem_tag WHERE tag_id = " + tag.getId());
        }
        wrapper.last("ORDER BY RAND() LIMIT 1");
        Problem problem = baseMapper.selectOne(wrapper);
        BusinessException.throwIf(problem == null, ProblemErrorCode.PROBLEM_NOT_FOUND);
        return toVO(problem, getTagNames(problem.getId()), getAttachments(problem.getId()));
    }

    // ==================== AI 客服查询 ====================

    /**
     * 查询供 AI 客服使用的最小已发布题目事实。
     *
     * @param query 受控查询条件
     * @return 题目工具结果
     */
    @Override
    public AssistantProblemResultDTO queryForAssistant(AssistantProblemQueryDTO query) {
        // 防御服务层直接调用
        BusinessException.throwIf(
                query == null || query.getMode() == null || !query.isModeFieldsValid(),
                ErrorCodeEnum.PARAM_INVALID
        );

        // 查询比返回上限多一条，用于标记候选截断
        int limit = assistantLimit(query);
        List<Problem> selected = selectAssistantProblems(query, limit + 1);
        boolean truncated = selected.size() > limit;
        List<Problem> problems = truncated ? selected.subList(0, limit) : selected;

        // 批量组装标签、赛事和受限题面概览
        List<Long> problemIds = problems.stream().map(Problem::getId).toList();
        Map<Long, List<String>> tagMap = batchGetTagNames(problemIds);
        Map<Long, Contest> contestMap = batchGetContests(problems);
        List<AssistantProblemResultDTO.Item> items = new ArrayList<>();
        for (Problem problem : problems) {
            OverviewValue overview = overview(problem, query);
            truncated = truncated || overview.truncated();
            Contest contest = contestMap.get(problem.getContestId());
            items.add(new AssistantProblemResultDTO.Item(
                    problem.getCode(),
                    problem.getTitle(),
                    contest == null ? null : contest.getCode(),
                    contest == null ? null : contest.getName(),
                    problem.getYear(),
                    problem.getStatementLanguage(),
                    problem.getDifficulty(),
                    problem.getDurationMinutes(),
                    tagMap.getOrDefault(problem.getId(), List.of()),
                    overview.text()
            ));
        }
        return new AssistantProblemResultDTO(
                items,
                assistantMatchType(query),
                truncated,
                matchedConditions(query)
        );
    }

    /**
     * 查询符合条件的已发布题目。
     *
     * @param query 受控查询条件
     * @param fetchLimit 数据库读取上限
     * @return 稳定排序的候选
     */
    private List<Problem> selectAssistantProblems(AssistantProblemQueryDTO query, int fetchLimit) {
        LambdaQueryWrapper<Problem> wrapper = new LambdaQueryWrapper<Problem>()
                .eq(Problem::getStatus, 1);
        String keyword = normalized(query.getKeyword());
        if (query.getMode() == AssistantProblemQueryMode.SEARCH) {
            wrapper.eq(query.getCode() != null, Problem::getCode, query.getCode())
                    .like(keyword != null, Problem::getTitle, keyword);
        } else {
            applyRecommendationKeyword(wrapper, keyword);
            Contest contest = assistantContest(query.getContestCode());
            if (contest != null) wrapper.eq(Problem::getContestId, contest.getId());
            if (query.getYear() != null) wrapper.eq(Problem::getYear, query.getYear());
            if (query.getDifficulty() != null) wrapper.eq(Problem::getDifficulty, query.getDifficulty());
            if (query.getStatementLanguage() != null) {
                wrapper.eq(Problem::getStatementLanguage, query.getStatementLanguage());
            }
            if (query.getMaxDurationMinutes() != null) {
                wrapper.le(Problem::getDurationMinutes, query.getMaxDurationMinutes());
            }
        }
        wrapper.orderByDesc(Problem::getYear)
                .orderByAsc(Problem::getCode)
                .last("LIMIT " + fetchLimit);
        return baseMapper.selectList(wrapper);
    }

    /**
     * 应用标题或标签关键词条件。
     *
     * @param wrapper 题目查询
     * @param keyword 标准化关键词
     */
    private void applyRecommendationKeyword(LambdaQueryWrapper<Problem> wrapper, String keyword) {
        if (keyword == null) return;
        List<Tag> tags = tagMapper.selectList(new LambdaQueryWrapper<Tag>().like(Tag::getName, keyword));
        if (tags.isEmpty()) {
            wrapper.like(Problem::getTitle, keyword);
            return;
        }
        List<Long> problemIds = problemTagMapper.selectList(new LambdaQueryWrapper<ProblemTag>()
                        .in(ProblemTag::getTagId, tags.stream().map(Tag::getId).toList()))
                .stream()
                .map(ProblemTag::getProblemId)
                .distinct()
                .toList();
        if (problemIds.isEmpty()) {
            wrapper.like(Problem::getTitle, keyword);
            return;
        }
        wrapper.and(nested -> nested.like(Problem::getTitle, keyword)
                .or()
                .in(Problem::getId, problemIds));
    }

    /**
     * 按赛事编码解析赛事。
     *
     * @param contestCode 赛事编码
     * @return 赛事；未传时为 null
     */
    private Contest assistantContest(String contestCode) {
        if (contestCode == null) return null;
        Contest contest = contestMapper.selectOne(new LambdaQueryWrapper<Contest>()
                .eq(Contest::getCode, contestCode));
        BusinessException.throwIf(contest == null, ProblemErrorCode.CONTEST_NOT_FOUND);
        return contest;
    }

    /**
     * 计算工具返回数量。
     *
     * @param query 查询条件
     * @return 1 到 5 的返回上限
     */
    private int assistantLimit(AssistantProblemQueryDTO query) {
        if (query.getMode() == AssistantProblemQueryMode.SEARCH && query.getCode() != null) return 1;
        int defaultLimit = query.getMode() == AssistantProblemQueryMode.RECOMMEND ? 3 : 5;
        int requested = query.getLimit() == null ? defaultLimit : query.getLimit();
        return Math.max(1, Math.min(requested, 5));
    }

    /**
     * 生成最多 500 个 Unicode 码点的题面概览。
     *
     * @param problem 题目
     * @param query 查询条件
     * @return 概览与截断标识
     */
    private OverviewValue overview(Problem problem, AssistantProblemQueryDTO query) {
        if (query.getMode() != AssistantProblemQueryMode.SEARCH
                || !Boolean.TRUE.equals(query.getIncludeOverview())) {
            return new OverviewValue(null, false);
        }
        String markdown = problem.getContentMarkdown();
        if (markdown == null || markdown.isBlank()) return new OverviewValue(null, false);
        int codePoints = markdown.codePointCount(0, markdown.length());
        if (codePoints <= 500) return new OverviewValue(markdown, false);
        int end = markdown.offsetByCodePoints(0, 500);
        return new OverviewValue(markdown.substring(0, end), true);
    }

    /**
     * 返回匹配方式。
     *
     * @param query 查询条件
     * @return 匹配方式
     */
    private String assistantMatchType(AssistantProblemQueryDTO query) {
        if (query.getMode() == AssistantProblemQueryMode.RECOMMEND) return "FILTER";
        return query.getCode() == null ? "KEYWORD" : "CODE";
    }

    /**
     * 返回已经应用的推荐条件。
     *
     * @param query 查询条件
     * @return 稳定顺序的条件摘要
     */
    private List<String> matchedConditions(AssistantProblemQueryDTO query) {
        if (query.getMode() != AssistantProblemQueryMode.RECOMMEND) return List.of();
        List<String> conditions = new ArrayList<>();
        if (normalized(query.getKeyword()) != null) conditions.add("keyword:" + normalized(query.getKeyword()));
        if (query.getContestCode() != null) conditions.add("contestCode:" + query.getContestCode());
        if (query.getYear() != null) conditions.add("year:" + query.getYear());
        if (query.getDifficulty() != null) conditions.add("difficulty:" + query.getDifficulty());
        if (query.getStatementLanguage() != null) {
            conditions.add("statementLanguage:" + query.getStatementLanguage());
        }
        if (query.getMaxDurationMinutes() != null) {
            conditions.add("maxDurationMinutes:" + query.getMaxDurationMinutes());
        }
        return conditions;
    }

    /**
     * 标准化可空关键词。
     *
     * @param value 原始值
     * @return 去除首尾空白后的值
     */
    private String normalized(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }

    /** 题面概览和截断标识。 */
    private record OverviewValue(String text, boolean truncated) {
    }

    // ==================== 创建 ====================

    /**
     * 创建新的建模题目并持久化初始标签关联。
     *
     * @param request   题目创建参数对象，不能为 null
     * @param creatorId 创建人用户 ID，不能为 null
     * @return 创建成功后的题目视图对象
     */
    @Override
    @Transactional
    public ProblemVO createProblem(ProblemCreateRequest request, Long creatorId) {
        validateContest(request.getContestId());

        Problem problem = new Problem();
        problem.setTitle(request.getTitle());
        problem.setContentMarkdown(request.getContentMarkdown());
        problem.setSolutionHint(normalized(request.getSolutionHint()));
        problem.setContestId(request.getContestId());
        problem.setProblemNumber(request.getProblemNumber());
        problem.setYear(request.getYear());
        problem.setStatementLanguage(request.getStatementLanguage());
        problem.setDurationMinutes(request.getDurationMinutes());
        problem.setDifficulty(request.getDifficulty());
        problem.setStatus(request.getStatus() != null ? request.getStatus() : 0);
        problem.setAverageScore(BigDecimal.ZERO);
        problem.setCreatorId(creatorId);
        problem.setCode(nextProblemCode());

        save(problem);
        audit.problemCreated(problem.getId());
        log.info("创建题目完成: id={}", problem.getId());

        // 保存标签
        List<String> tagNames = saveTags(problem.getId(), request.getTagIds());
        recordPublicInvalidation();
        return toVO(problem, tagNames, List.of());
    }

    /**
     * 生成下一个题号：基于现有最大 code + 1，起始从 1 开始依次增加。
     * 标识是标识（雪花主键 ID），题号是题号（code），面向用户展示自然连续序列。
     */
    private int nextProblemCode() {
        Integer maxCode = baseMapper.selectMaxCode();
        int next = (maxCode == null || maxCode < 1) ? 1 : maxCode + 1;
        BusinessException.throwIf(next > 100000, ProblemErrorCode.PROBLEM_POOL_EXHAUSTED);
        return next;
    }

    // ==================== 更新 ====================

    /**
     * 更新已有题目的基本信息、题面 Markdown 或发布状态。
     *
     * @param id      目标题目 ID，不能为 null
     * @param request 包含修改内容的请求对象，不能为 null
     * @return 更新后的题目视图对象
     * @throws BusinessException 若题目不存在
     */
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
        if (request.getContentMarkdown() != null) {
            problem.setContentMarkdown(request.getContentMarkdown().isEmpty()
                    ? null : request.getContentMarkdown());
            changed = true;
        }
        if (request.getSolutionHint() != null) {
            problem.setSolutionHint(normalized(request.getSolutionHint()));
            changed = true;
        }
        if (request.getContestId() != null) {
            problem.setContestId(request.getContestId());
            changed = true;
        }
        if (request.getProblemNumber() != null) {
            problem.setProblemNumber(request.getProblemNumber());
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

        // 标签：null 不修改，非 null 替换
        List<String> tagNames = getTagNames(id);
        if (request.getTagIds() != null) {
            tagNames = replaceTags(id, request.getTagIds());
        }
        if (changed || request.getTagIds() != null) audit.problemUpdated(id);

        log.info("更新题目: {}", id);
        recordPublicInvalidation();
        return toVO(problem, tagNames, getAttachments(id));
    }

    /**
     * 删除题目及其从属数据。
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

        List<ProblemAttachment> attachments = getAttachments(id);
        LambdaQueryWrapper<ProblemAttachment> attachmentWrapper = new LambdaQueryWrapper<>();
        attachmentWrapper.eq(ProblemAttachment::getProblemId, id);
        problemAttachmentMapper.delete(attachmentWrapper);

        // 逻辑删除题目
        removeById(id);
        audit.problemDeleted(id);
        // 附件关系解除后由 file-service 依据引用投影决定何时物理清理对象。
        attachments.forEach(attachment -> attachmentEvents.unbound(attachment.getId(), attachment.getFileId()));
        recordPublicInvalidation();
        log.info("删除题目: {}", id);
    }

    // ==================== 附件管理 ====================

    /**
     * 上传题目附件。
     * @param problemId 题目 ID
     * @param file 附件文件
     * @param description 附件说明
     * @param sortOrder 展示顺序
     * @return 附件响应
     */
    @Override
    @Transactional
    public ProblemVO.AttachmentVO uploadAttachment(
            Long problemId,
            MultipartFile file,
            String description,
            Integer sortOrder
    ) {
        // 校验题目存在
        BusinessException.throwIf(getById(problemId) == null, ProblemErrorCode.PROBLEM_NOT_FOUND);

        // 物理对象与访问策略由 file-service 管理，题目服务只保存稳定 fileId
        FileAssetSummaryDTO asset = registerAttachment(problemId, file);
        ProblemAttachment attachment = new ProblemAttachment();
        attachment.setProblemId(problemId);
        attachment.setFileId(asset.fileId());
        attachment.setFileName(normalizeFileName(file.getOriginalFilename()));
        attachment.setContentType(file.getContentType() == null
                ? "application/octet-stream" : file.getContentType());
        attachment.setFileSize(file.getSize());
        attachment.setDescription(description);
        attachment.setSortOrder(sortOrder == null ? 0 : sortOrder);

        problemAttachmentMapper.insert(attachment);
        // 附件关系与绑定事件在同一事务内落库，保证引用投影最终一致。
        attachmentEvents.bound(attachment.getId(), asset.fileId());
        recordPublicInvalidation();
        return toAttachmentVO(attachment);
    }

    /**
     * 删除题目附件。
     * @param problemId 题目 ID
     * @param attachmentId 附件 ID
     */
    @Override
    @Transactional
    public void deleteAttachment(Long problemId, Long attachmentId) {
        // 校验附件归属
        ProblemAttachment attachment = problemAttachmentMapper.selectById(attachmentId);
        BusinessException.throwIf(
                attachment == null || !problemId.equals(attachment.getProblemId()),
                ProblemErrorCode.ATTACHMENT_NOT_FOUND
        );

        // 只解除业务关系；物理清理由 file-service 依据引用投影在宽限期后执行
        problemAttachmentMapper.deleteById(attachmentId);
        attachmentEvents.unbound(attachmentId, attachment.getFileId());
        audit.attachmentDeleted(attachmentId);
        recordPublicInvalidation();
    }

    @Override
    @Transactional
    public ProblemVO.AttachmentVO attachRegisteredFile(
            Long problemId, com.leetmodel.problem.dto.ProblemAttachmentRegisterRequest request) {
        BusinessException.throwIf(getById(problemId) == null, ProblemErrorCode.PROBLEM_NOT_FOUND);
        FileAssetSummaryDTO asset = requireRegisteredAttachment(request.getFileId());

        ProblemAttachment attachment = new ProblemAttachment();
        attachment.setProblemId(problemId);
        attachment.setFileId(asset.fileId());
        attachment.setFileName(normalizeFileName(asset.originalName()));
        attachment.setContentType(asset.contentType() == null
                ? "application/octet-stream" : asset.contentType());
        attachment.setFileSize(asset.fileSize());
        attachment.setDescription(request.getDescription());
        attachment.setSortOrder(request.getSortOrder() == null ? 0 : request.getSortOrder());
        problemAttachmentMapper.insert(attachment);
        // 直传完成的资产仍处于未绑定状态，这里补齐题目附件引用
        attachmentEvents.bound(attachment.getId(), asset.fileId());
        recordPublicInvalidation();
        return toAttachmentVO(attachment);
    }

    /**
     * 校验并读取已登记的题目附件资产。
     *
     * @param fileId 文件资产标识
     * @return 文件资产摘要
     */
    private FileAssetSummaryDTO requireRegisteredAttachment(Long fileId) {
        Result<FileAssetSummaryDTO> result = fileFeignClient.getSummary(fileId);
        if (result == null || result.getCode() != RESULT_SUCCESS_CODE || result.getData() == null) {
            throw new BusinessException(ProblemErrorCode.ATTACHMENT_REGISTER_FAILED, "文件资产不可用");
        }
        FileAssetSummaryDTO asset = result.getData();
        if (!ProblemAttachmentEventProducer.RESOURCE_TYPE.equals(asset.sourceType())) {
            throw new BusinessException(ProblemErrorCode.ATTACHMENT_REGISTER_FAILED, "文件用途不是题目附件");
        }
        return asset;
    }

    // ==================== 标签名称查询 ====================

    /**
     * 查询指定题目关联的所有标签名称列表。
     *
     * @param problemId 目标题目 ID，不能为 null
     * @return 标签名称字符串列表
     */
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

    /** 查询指定题目关联的标签名称及其业务分类，仅用于详情响应。 */
    private List<ProblemVO.TagVO> getTagDetails(Long problemId) {
        List<Long> tagIds = problemTagMapper.selectList(new LambdaQueryWrapper<ProblemTag>()
                        .eq(ProblemTag::getProblemId, problemId))
                .stream()
                .map(ProblemTag::getTagId)
                .toList();
        if (tagIds.isEmpty()) return List.of();
        return tagMapper.selectBatchIds(tagIds).stream()
                .map(tag -> ProblemVO.TagVO.builder()
                        .id(tag.getId())
                        .name(tag.getName())
                        .type(tag.getType())
                        .build())
                .toList();
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
     * 批量获取当前页题目所属赛事。
     */
    private Map<Long, Contest> batchGetContests(List<Problem> problems) {
        List<Long> contestIds = problems.stream()
                .map(Problem::getContestId)
                .distinct()
                .toList();
        if (contestIds.isEmpty()) return Map.of();
        return contestMapper.selectBatchIds(contestIds).stream()
                .collect(Collectors.toMap(Contest::getId, contest -> contest));
    }

    /**
     * 保存题目标签关联，返回标签名称列表。
     */
    private List<String> saveTags(Long problemId, List<Long> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) return List.of();

        List<Tag> tags = validateTags(tagIds);

        // 保存题目标签关系
        for (Tag tag : tags) {
            ProblemTag pt = new ProblemTag();
            pt.setProblemId(problemId);
            pt.setTagId(tag.getId());
            problemTagMapper.insert(pt);
        }
        return tags.stream().map(Tag::getName).toList();
    }

    /**
     * 校验标签存在；背景领域最多选择一个，题目类型与模型算法允许多选。
     * 数学建模实际赛题常包含多个题型不同的小问，允许单题关联多个题目类型标签。
     */
    private List<Tag> validateTags(List<Long> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) return List.of();

        List<Long> uniqueTagIds = new ArrayList<>(new LinkedHashSet<>(tagIds));
        List<Tag> tags = tagMapper.selectBatchIds(uniqueTagIds);
        BusinessException.throwIf(tags.size() != uniqueTagIds.size(), ProblemErrorCode.TAG_NOT_FOUND);

        Map<String, Long> typeCounts = tags.stream()
                .collect(Collectors.groupingBy(Tag::getType, Collectors.counting()));
        boolean hasExclusiveTypeConflict = typeCounts.entrySet().stream()
                .anyMatch(entry -> TagType.BACKGROUND_DOMAIN.name().equals(entry.getKey())
                        && entry.getValue() > 1);
        BusinessException.throwIf(hasExclusiveTypeConflict, ProblemErrorCode.TAG_TYPE_CONFLICT);
        return tags;
    }

    private void validateScoreRange(ProblemPageQuery query) {
        BusinessException.throwIf(
                query.getMinAverageScore() != null
                        && query.getMaxAverageScore() != null
                        && query.getMinAverageScore().compareTo(query.getMaxAverageScore()) > 0,
                ProblemErrorCode.INVALID_SCORE_RANGE
        );
    }

    /**
     * 应用公开题库白名单排序，避免将客户端字段名直接拼接进 SQL。
     */
    private void applySort(LambdaQueryWrapper<Problem> wrapper, ProblemPageQuery query) {
        boolean ascending = "asc".equals(query.getSortOrder());
        if ("code".equals(query.getSortBy())) {
            wrapper.orderBy(true, ascending, Problem::getCode);
            wrapper.orderBy(true, ascending, Problem::getId);
        } else if ("year".equals(query.getSortBy())) {
            wrapper.orderBy(true, ascending, Problem::getYear);
            wrapper.orderByDesc(Problem::getId);
        } else if ("difficulty".equals(query.getSortBy())) {
            wrapper.orderBy(true, ascending, Problem::getDifficulty);
            wrapper.orderByDesc(Problem::getId);
        } else if ("averageScore".equals(query.getSortBy())) {
            wrapper.orderBy(true, ascending, Problem::getAverageScore);
            wrapper.orderByDesc(Problem::getId);
        } else {
            wrapper.orderByAsc(Problem::getCode);
            wrapper.orderByAsc(Problem::getId);
        }
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
     * 获取题目附件列表。
     * @param problemId 题目 ID
     * @return 附件列表
     */
    private List<ProblemAttachment> getAttachments(Long problemId) {
        LambdaQueryWrapper<ProblemAttachment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProblemAttachment::getProblemId, problemId);
        wrapper.orderByAsc(ProblemAttachment::getSortOrder)
                .orderByAsc(ProblemAttachment::getCreateTime);
        return problemAttachmentMapper.selectList(wrapper);
    }

    /**
     * 校验赛事合法性。
     */
    private void validateContest(Long contestId) {
        Contest contest = contestMapper.selectById(contestId);
        BusinessException.throwIf(contest == null, ProblemErrorCode.CONTEST_NOT_FOUND);
    }

    /**
     * Problem 实体转 ProblemVO。
     */
    private ProblemVO toVO(
            Problem p,
            List<String> tagNames,
            List<ProblemAttachment> attachments
    ) {
        return toVO(p, tagNames, attachments, contestMapper.selectById(p.getContestId()));
    }

    private ProblemVO toVO(
            Problem p,
            List<String> tagNames,
            List<ProblemAttachment> attachments,
            Contest contest
    ) {
        ProblemVO.ProblemVOBuilder builder = ProblemVO.builder()
                .id(p.getId())
                .code(p.getCode())
                .problemNumber(p.getProblemNumber())
                .title(p.getTitle())
                .contentMarkdown(attachments == null ? null : p.getContentMarkdown())
                .solutionHint(attachments == null ? null : p.getSolutionHint())
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
                .tags(attachments == null ? null : getTagDetails(p.getId()));

        if (attachments != null) {
            builder.attachments(attachments.stream()
                    .map(this::toAttachmentVO)
                    .toList());
        }

        return builder.build();
    }

    /**
     * 附件实体转响应。
     * @param attachment 附件实体
     * @return 附件响应
     */
    private ProblemVO.AttachmentVO toAttachmentVO(ProblemAttachment attachment) {
        return ProblemVO.AttachmentVO.builder()
                .id(attachment.getId())
                .fileName(attachment.getFileName())
                .contentType(attachment.getContentType())
                .fileSize(attachment.getFileSize())
                .description(attachment.getDescription())
                .sortOrder(attachment.getSortOrder())
                .downloadUrl(resolveDownloadUrl(attachment.getFileId()))
                .build();
    }

    /**
     * 获取已启用的存储服务。
     * @return 存储服务
     */
    /**
     * 通过 file-service 登记附件资产。
     *
     * <p>附件物理路由与访问策略由 file-service 决定，题目服务只声明用途与逻辑分组。</p>
     *
     * @param problemId 题目标识，用于逻辑分组
     * @param file 附件文件
     * @return 文件资产摘要
     */
    private FileAssetSummaryDTO registerAttachment(Long problemId, MultipartFile file) {
        Result<FileAssetSummaryDTO> result;
        try {
            result = fileFeignClient.registerForPurpose(
                    ProblemAttachmentEventProducer.PURPOSE_CODE,
                    "problems/" + problemId,
                    null,
                    file);
        } catch (RuntimeException exception) {
            log.warn("附件登记调用失败: problemId={}, exceptionType={}",
                    problemId, exception.getClass().getSimpleName());
            throw new BusinessException(ProblemErrorCode.ATTACHMENT_STORAGE_UNAVAILABLE);
        }
        if (result == null || result.getCode() != RESULT_SUCCESS_CODE
                || result.getData() == null || result.getData().fileId() == null) {
            throw new BusinessException(ProblemErrorCode.ATTACHMENT_REGISTER_FAILED);
        }
        return result.getData();
    }

    /**
     * 按 fileId 生成短时效下载地址。
     *
     * <p>公开题库详情不应因附件存储短暂不可用而整体失败，因此失败时返回 null 并记录告警。</p>
     *
     * @param fileId 文件资产标识
     * @return 预签名下载地址；不可用时为 null
     */
    private String resolveDownloadUrl(Long fileId) {
        if (fileId == null) {
            return null;
        }
        try {
            Result<FileAccessUrlDTO> result = fileFeignClient.createAccessUrl(fileId);
            if (result == null || result.getCode() != RESULT_SUCCESS_CODE || result.getData() == null) {
                log.warn("附件访问地址生成失败: fileId={}, code={}",
                        fileId, result == null ? null : result.getCode());
                return null;
            }
            return result.getData().url();
        } catch (RuntimeException exception) {
            log.warn("附件访问地址生成异常: fileId={}, exceptionType={}",
                    fileId, exception.getClass().getSimpleName());
            return null;
        }
    }

    /**
     * 标准化附件展示文件名。
     * @param originalFilename 原始文件名
     * @return 展示文件名
     */
    private String normalizeFileName(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) return "attachment";
        return originalFilename;
    }

    /**
     * 在当前业务事务中记录公开题库失效事件。
     */
    private void recordPublicInvalidation() {
        cacheInvalidator.record(
                ProblemPublicCacheService.REGION,
                ProblemPublicCacheService.SCOPE,
                ProblemPublicCacheService.SCHEMA_VERSION
        );
    }
}
