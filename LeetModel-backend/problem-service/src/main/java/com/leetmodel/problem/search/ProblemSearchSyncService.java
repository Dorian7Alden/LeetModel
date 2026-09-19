package com.leetmodel.problem.search;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.leetmodel.problem.entity.Problem;
import com.leetmodel.problem.entity.ProblemTag;
import com.leetmodel.problem.mapper.ProblemMapper;
import com.leetmodel.problem.mapper.ProblemTagMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 题库索引同步。
 *
 * <p>题目写操作提交后按需增量同步；索引缺失时在启动阶段按数据库快照重建。</p>
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "problem.search", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ProblemSearchSyncService implements ApplicationRunner {

    private final ProblemMapper problemMapper;
    private final ProblemTagMapper problemTagMapper;
    private final ProblemSearchIndexService indexService;

    public ProblemSearchSyncService(ProblemMapper problemMapper,
                                    ProblemTagMapper problemTagMapper,
                                    ProblemSearchIndexService indexService) {
        this.problemMapper = problemMapper;
        this.problemTagMapper = problemTagMapper;
        this.indexService = indexService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!indexService.indexExists()) {
            int indexed = rebuildAll();
            log.info("题库索引首次创建并完成重建: indexed={}", indexed);
        }
    }

    /**
     * 增量同步单个题目。
     *
     * @param problemId 题目标识
     */
    public void sync(Long problemId) {
        Problem problem = problemMapper.selectById(problemId);
        if (problem == null) {
            indexService.deleteDocument(problemId);
            return;
        }
        indexService.indexDocument(toDocument(problem, tagIds(problemId)));
    }

    /**
     * 删除题目文档。
     *
     * @param problemId 题目标识
     */
    public void remove(Long problemId) {
        indexService.deleteDocument(problemId);
    }

    /**
     * 按数据库快照全量重建索引。
     *
     * @return 成功写入的题目数量
     */
    public int rebuildAll() {
        List<Problem> problems = problemMapper.selectList(new LambdaQueryWrapper<>());
        List<ProblemSearchDocument> documents = problems.stream()
                .map(problem -> toDocument(problem, tagIds(problem.getId())))
                .toList();
        return indexService.rebuild(documents);
    }

    private List<Long> tagIds(Long problemId) {
        return problemTagMapper.selectList(new LambdaQueryWrapper<ProblemTag>()
                        .eq(ProblemTag::getProblemId, problemId))
                .stream()
                .map(ProblemTag::getTagId)
                .toList();
    }

    private ProblemSearchDocument toDocument(Problem problem, List<Long> tagIds) {
        return new ProblemSearchDocument(
                problem.getId(),
                problem.getCode(),
                problem.getProblemNumber(),
                problem.getTitle(),
                problem.getContentMarkdown(),
                problem.getContestId(),
                problem.getYear(),
                problem.getDifficulty(),
                problem.getStatementLanguage(),
                tagIds,
                problem.getStatus(),
                problem.getUpdateTime());
    }
}
