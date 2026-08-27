package com.leetmodel.problem.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.leetmodel.common.core.exception.BusinessException;
import com.leetmodel.common.core.storage.StorageService;
import com.leetmodel.problem.dto.ProblemCreateRequest;
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
import com.leetmodel.problem.service.ProblemService;
import com.leetmodel.problem.vo.ProblemVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
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

    private final ProblemTagMapper problemTagMapper;
    private final TagMapper tagMapper;
    private final ProblemAttachmentMapper problemAttachmentMapper;
    private final ContestMapper contestMapper;
    private final ObjectProvider<StorageService> storageServiceProvider;

    // ==================== 分页查询 ====================

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
        Problem problem = getById(id);
        BusinessException.throwIf(
                problem == null || !Integer.valueOf(1).equals(problem.getStatus()),
                ProblemErrorCode.PROBLEM_NOT_FOUND
        );
        List<String> tagNames = getTagNames(id);
        List<ProblemAttachment> attachments = getAttachments(id);
        return toVO(problem, tagNames, attachments);
    }

    @Override
    public ProblemVO getRandomPublishedProblem(ProblemPageQuery query) {
        validateScoreRange(query);
        LambdaQueryWrapper<Problem> wrapper = new LambdaQueryWrapper<Problem>()
                .eq(Problem::getStatus, 1);
        if (query.getContestId() != null) wrapper.eq(Problem::getContestId, query.getContestId());
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

    // ==================== 创建 ====================

    @Override
    @Transactional
    public ProblemVO createProblem(ProblemCreateRequest request, Long creatorId) {
        validateContest(request.getContestId());

        Problem problem = new Problem();
        problem.setTitle(request.getTitle());
        problem.setContentMarkdown(request.getContentMarkdown());
        problem.setContestId(request.getContestId());
        problem.setYear(request.getYear());
        problem.setStatementLanguage(request.getStatementLanguage());
        problem.setDurationMinutes(request.getDurationMinutes());
        problem.setDifficulty(request.getDifficulty());
        problem.setStatus(request.getStatus() != null ? request.getStatus() : 0);
        problem.setAverageScore(BigDecimal.ZERO);
        problem.setCreatorId(creatorId);
        problem.setCode(nextProblemCode());

        save(problem);
        log.info("创建题目: {} [ID: {}]", problem.getTitle(), problem.getId());

        // 保存标签
        List<String> tagNames = saveTags(problem.getId(), request.getTagIds());
        return toVO(problem, tagNames, List.of());
    }

    /**
     * 生成下一个短题号：基于现有最大 code + 1，起始 1001，上限 10000。
     * 题目量有限（通常 <= 10000），该编号用于用户展示，不暴露内部雪花主键。
     */
    private int nextProblemCode() {
        Integer maxCode = baseMapper.selectMaxCode();
        int next = maxCode == null ? 1001 : maxCode + 1;
        BusinessException.throwIf(next > 10000, ProblemErrorCode.PROBLEM_POOL_EXHAUSTED);
        return next;
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
        if (request.getContentMarkdown() != null) {
            problem.setContentMarkdown(request.getContentMarkdown().isEmpty()
                    ? null : request.getContentMarkdown());
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

        // 标签：null 不修改，非 null 替换
        List<String> tagNames = getTagNames(id);
        if (request.getTagIds() != null) {
            tagNames = replaceTags(id, request.getTagIds());
        }

        log.info("更新题目: {}", id);
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
        deleteObjectsAfterCommit(attachments.stream()
                .map(ProblemAttachment::getObjectKey)
                .toList());
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
    public ProblemVO.AttachmentVO uploadAttachment(
            Long problemId,
            MultipartFile file,
            String description,
            Integer sortOrder
    ) {
        // 校验题目与存储服务
        BusinessException.throwIf(getById(problemId) == null, ProblemErrorCode.PROBLEM_NOT_FOUND);
        StorageService storageService = getStorageService();

        // 先上传对象，再保存元数据
        String objectKey = storageService.upload(file, "problems/" + problemId + "/attachments");
        ProblemAttachment attachment = new ProblemAttachment();
        attachment.setProblemId(problemId);
        attachment.setFileName(normalizeFileName(file.getOriginalFilename()));
        attachment.setObjectKey(objectKey);
        attachment.setContentType(file.getContentType() == null
                ? "application/octet-stream" : file.getContentType());
        attachment.setFileSize(file.getSize());
        attachment.setDescription(description);
        attachment.setSortOrder(sortOrder == null ? 0 : sortOrder);

        try {
            problemAttachmentMapper.insert(attachment);
        } catch (RuntimeException exception) {
            deleteUploadedObject(storageService, objectKey);
            throw exception;
        }
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

        // 删除元数据，提交后删除对象
        problemAttachmentMapper.deleteById(attachmentId);
        deleteObjectsAfterCommit(List.of(attachment.getObjectKey()));
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
     * 校验标签存在；背景领域与题目类型最多选择一个，模型算法允许多选。
     */
    private List<Tag> validateTags(List<Long> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) return List.of();

        List<Long> uniqueTagIds = new ArrayList<>(new LinkedHashSet<>(tagIds));
        List<Tag> tags = tagMapper.selectBatchIds(uniqueTagIds);
        BusinessException.throwIf(tags.size() != uniqueTagIds.size(), ProblemErrorCode.TAG_NOT_FOUND);

        Map<String, Long> typeCounts = tags.stream()
                .collect(Collectors.groupingBy(Tag::getType, Collectors.counting()));
        boolean hasExclusiveTypeConflict = typeCounts.entrySet().stream()
                .anyMatch(entry -> !TagType.MODEL_ALGORITHM.name().equals(entry.getKey())
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
        if ("year".equals(query.getSortBy())) {
            wrapper.orderBy(true, ascending, Problem::getYear);
        } else if ("difficulty".equals(query.getSortBy())) {
            wrapper.orderBy(true, ascending, Problem::getDifficulty);
        } else if ("averageScore".equals(query.getSortBy())) {
            wrapper.orderBy(true, ascending, Problem::getAverageScore);
        } else {
            wrapper.orderByDesc(Problem::getCreateTime);
        }
        wrapper.orderByDesc(Problem::getId);
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
                .title(p.getTitle())
                .contentMarkdown(attachments == null ? null : p.getContentMarkdown())
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
        StorageService storageService = storageServiceProvider.getIfAvailable();
        return ProblemVO.AttachmentVO.builder()
                .id(attachment.getId())
                .fileName(attachment.getFileName())
                .contentType(attachment.getContentType())
                .fileSize(attachment.getFileSize())
                .description(attachment.getDescription())
                .sortOrder(attachment.getSortOrder())
                .downloadUrl(storageService == null
                        ? null : storageService.getUrl(attachment.getObjectKey()))
                .build();
    }

    /**
     * 获取已启用的存储服务。
     * @return 存储服务
     */
    private StorageService getStorageService() {
        StorageService storageService = storageServiceProvider.getIfAvailable();
        BusinessException.throwIf(storageService == null, ProblemErrorCode.STORAGE_NOT_ENABLED);
        return storageService;
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
     * 元数据保存失败时补偿删除已上传对象。
     * @param storageService 存储服务
     * @param objectKey 对象路径
     */
    private void deleteUploadedObject(StorageService storageService, String objectKey) {
        try {
            storageService.delete(objectKey);
        } catch (RuntimeException cleanupException) {
            log.error("附件元数据保存失败且对象清理失败: {}", objectKey, cleanupException);
        }
    }

    /**
     * 数据库事务提交后删除对象。
     * @param objectKeys 对象路径列表
     */
    private void deleteObjectsAfterCommit(List<String> objectKeys) {
        if (objectKeys.isEmpty()) return;
        Runnable deleteAction = () -> deleteObjects(objectKeys);
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            deleteAction.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                deleteAction.run();
            }
        });
    }

    /**
     * 删除对象存储中的附件。
     * @param objectKeys 对象路径列表
     */
    private void deleteObjects(List<String> objectKeys) {
        StorageService storageService = storageServiceProvider.getIfAvailable();
        if (storageService == null) {
            log.error("附件存储服务未启用，无法删除对象: {}", objectKeys);
            return;
        }
        for (String objectKey : objectKeys) {
            try {
                storageService.delete(objectKey);
            } catch (RuntimeException exception) {
                log.error("删除附件对象失败: {}", objectKey, exception);
            }
        }
    }
}
