package com.leetmodel.problem.cache;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leetmodel.common.cache.CacheKeyHasher;
import com.leetmodel.common.cache.CacheSpec;
import com.leetmodel.common.cache.CacheVersionProvider;
import com.leetmodel.common.cache.HttpCacheSupport;
import com.leetmodel.common.cache.MultiLevelCache;
import com.leetmodel.common.core.exception.BusinessException;
import com.leetmodel.common.core.result.PageResult;
import com.leetmodel.problem.dto.ProblemPageQuery;
import com.leetmodel.problem.entity.Contest;
import com.leetmodel.problem.entity.Tag;
import com.leetmodel.problem.enums.ProblemErrorCode;
import com.leetmodel.problem.mapper.ContestMapper;
import com.leetmodel.problem.mapper.TagMapper;
import com.leetmodel.problem.service.ProblemService;
import com.leetmodel.problem.vo.ProblemFilterOptionsVO;
import com.leetmodel.problem.vo.ProblemVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 公开题库的三级缓存读取与 HTTP 验证器。
 */
@Service
@RequiredArgsConstructor
public class ProblemPublicCacheService {

    public static final String REGION = "public";
    public static final String SCOPE = "all";
    public static final String SCHEMA_VERSION = "v1";

    private static final Duration NEGATIVE_LOCAL_TTL = Duration.ofSeconds(5);
    private static final Duration NEGATIVE_REDIS_TTL = Duration.ofSeconds(30);

    private final ProblemService problemService;
    private final ContestMapper contestMapper;
    private final TagMapper tagMapper;
    private final MultiLevelCache cache;
    private final CacheVersionProvider versionProvider;
    private final ObjectMapper objectMapper;

    /**
     * 读取公开筛选项。
     *
     * @return 筛选项
     */
    public ProblemFilterOptionsVO filterOptions() {
        CacheSpec spec = spec(
                "filter-options",
                Duration.ofMinutes(5),
                Duration.ofMinutes(30)
        );
        return cache.get(spec, objectMapper.constructType(ProblemFilterOptionsVO.class), this::loadFilterOptions);
    }

    /**
     * 分页读取已发布题目。
     *
     * @param query 公开查询条件
     * @return 分页结果
     */
    public PageResult<ProblemVO> page(ProblemPageQuery query) {
        query.setStatus(1);
        if (!isPageCacheable(query)) return PageResult.from(problemService.pageProblems(query));
        String logicalKey = pageLogicalKey(query);
        CacheSpec spec = spec(logicalKey, Duration.ofSeconds(30), Duration.ofMinutes(5));
        JavaType pageType = objectMapper.getTypeFactory()
                .constructParametricType(PageResult.class, ProblemVO.class);
        return cache.get(
                spec,
                pageType,
                () -> PageResult.from(problemService.pageProblems(query))
        );
    }

    /**
     * 读取已发布题目详情，并为附件重新生成预签名 URL。
     *
     * @param id 题目 ID
     * @return 公开题目详情
     */
    public ProblemVO detail(Long id) {
        CacheSpec spec = spec("detail:" + id, Duration.ofMinutes(2), Duration.ofMinutes(10));
        ProblemDetailReadModel readModel = cache.get(
                spec,
                objectMapper.constructType(ProblemDetailReadModel.class),
                () -> problemService.findPublishedProblemReadModel(id)
        );
        BusinessException.throwIf(readModel == null, ProblemErrorCode.PROBLEM_NOT_FOUND);
        return problemService.materializePublishedProblem(readModel);
    }

    /**
     * 返回筛选项 HTTP 验证器。
     *
     * @return HTTP 验证器
     */
    public HttpCacheSupport.Validator filterValidator() {
        return validator("filter-options", Duration.ofSeconds(60));
    }

    /**
     * 返回分页 HTTP 验证器。
     *
     * @param query 公开查询条件
     * @return HTTP 验证器
     */
    public HttpCacheSupport.Validator pageValidator(ProblemPageQuery query) {
        return validator(pageLogicalKey(query), Duration.ofSeconds(20));
    }

    /**
     * 返回题目详情 HTTP 验证器。签名时间桶避免已过期 URL 被无限 304。
     *
     * @param id 题目 ID
     * @return HTTP 验证器
     */
    public HttpCacheSupport.Validator detailValidator(Long id) {
        long signingBucket = Instant.now().getEpochSecond() / Duration.ofHours(1).toSeconds();
        return validator("detail:" + id + ":sign:" + signingBucket, Duration.ofSeconds(60));
    }

    /**
     * 判断分页查询是否允许进入三级缓存。
     *
     * @param query 公开查询条件
     * @return 是否允许缓存
     */
    public boolean isPageCacheable(ProblemPageQuery query) {
        return query.getPage() <= 10
                && query.getPageSize() <= 50
                && (query.getKeyword() == null || query.getKeyword().isBlank());
    }

    /**
     * 从 MySQL 读取筛选项。
     *
     * @return 筛选项
     */
    private ProblemFilterOptionsVO loadFilterOptions() {
        List<Contest> contests = contestMapper.selectList(
                new LambdaQueryWrapper<Contest>().orderByAsc(Contest::getId)
        );
        List<Tag> tags = tagMapper.selectList(
                new LambdaQueryWrapper<Tag>().orderByAsc(Tag::getType).orderByAsc(Tag::getName)
        );
        return new ProblemFilterOptionsVO(contests, tags);
    }

    /**
     * 创建题库缓存契约。
     *
     * @param logicalKey 逻辑 Key
     * @param localTtl Caffeine TTL
     * @param redisTtl Redis TTL
     * @return 缓存契约
     */
    private CacheSpec spec(String logicalKey, Duration localTtl, Duration redisTtl) {
        return new CacheSpec(
                REGION,
                SCOPE,
                SCHEMA_VERSION,
                logicalKey,
                localTtl,
                redisTtl,
                NEGATIVE_LOCAL_TTL,
                NEGATIVE_REDIS_TTL
        );
    }

    /**
     * 生成不含原始条件的分页逻辑 Key。
     *
     * @param query 公开查询条件
     * @return 分页逻辑 Key
     */
    private String pageLogicalKey(ProblemPageQuery query) {
        List<Long> tagIds = query.getTagIds() == null
                ? List.of() : new ArrayList<>(query.getTagIds());
        tagIds = tagIds.stream().distinct().sorted().toList();
        String canonical = String.join("|",
                Integer.toString(query.getPage()),
                Integer.toString(query.getPageSize()),
                value(query.getContestId()),
                value(query.getYear()),
                value(query.getStatementLanguage()),
                value(query.getDifficulty()),
                value(query.getMinAverageScore()),
                value(query.getMaxAverageScore()),
                value(query.getSortBy() == null ? "createdAt" : query.getSortBy()),
                value(query.getSortOrder() == null ? "desc" : query.getSortOrder()),
                tagIds.toString()
        );
        return "page:" + CacheKeyHasher.sha256(canonical);
    }

    /**
     * 创建 HTTP 缓存验证器。
     *
     * @param logicalKey 逻辑 Key
     * @param maxAge L1 新鲜期
     * @return HTTP 验证器
     */
    private HttpCacheSupport.Validator validator(String logicalKey, Duration maxAge) {
        return HttpCacheSupport.validator(
                versionProvider.current(REGION, SCOPE),
                SCHEMA_VERSION,
                logicalKey,
                maxAge
        );
    }

    /**
     * 将可空查询值标准化为字符串。
     *
     * @param value 查询值
     * @return 标准化字符串
     */
    private String value(Object value) {
        if (value == null) return "_";
        if (value instanceof BigDecimal decimal) return decimal.stripTrailingZeros().toPlainString();
        return value.toString().trim();
    }
}
