package com.leetmodel.problem.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.leetmodel.problem.dto.ProblemCreateRequest;
import com.leetmodel.problem.dto.ProblemPageQuery;
import com.leetmodel.problem.dto.ProblemUpdateRequest;
import com.leetmodel.problem.entity.Problem;
import com.leetmodel.problem.vo.ProblemVO;
import com.leetmodel.problem.cache.ProblemDetailReadModel;
import com.leetmodel.common.api.dto.AssistantProblemQueryDTO;
import com.leetmodel.common.api.dto.AssistantProblemResultDTO;
import org.springframework.web.multipart.MultipartFile;

/**
 * 题目服务接口。
 */
public interface ProblemService extends IService<Problem> {

    /**
     * 分页查询题目（含标签名称）。
     */
    IPage<ProblemVO> pageProblems(ProblemPageQuery query);

    /**
     * 查询题目详情。
     */
    ProblemVO getProblemDetail(Long id);

    /**
     * 查询已发布题目详情。
     * @param id 题目 ID
     * @return 已发布题目详情
     */
    ProblemVO getPublishedProblemDetail(Long id);

    /**
     * 查找不含预签名 URL 的已发布题目读模型。
     * @param id 题目 ID
     * @return 稳定读模型；题目不存在或未发布时为 null
     */
    ProblemDetailReadModel findPublishedProblemReadModel(Long id);

    /**
     * 为稳定题目读模型生成当前附件下载 URL。
     * @param readModel 稳定读模型
     * @return 公开题目响应
     */
    ProblemVO materializePublishedProblem(ProblemDetailReadModel readModel);

    ProblemVO getRandomPublishedProblem(ProblemPageQuery query);

    /**
     * 查询供 AI 客服使用的最小已发布题目事实。
     * @param query 受控查询条件
     * @return 题目工具结果
     */
    AssistantProblemResultDTO queryForAssistant(AssistantProblemQueryDTO query);

    /**
     * 创建题目。
     */
    ProblemVO createProblem(ProblemCreateRequest request, Long creatorId);

    /**
     * 更新题目。
     */
    ProblemVO updateProblem(Long id, ProblemUpdateRequest request);

    /**
     * 删除题目及其从属数据。
     * @param id 题目 ID
     */
    void deleteProblem(Long id);

    /**
     * 上传题目附件。
     * @param problemId 题目 ID
     * @param file 附件文件
     * @param description 附件说明
     * @param sortOrder 展示顺序
     * @return 附件响应
     */
    ProblemVO.AttachmentVO uploadAttachment(
            Long problemId,
            MultipartFile file,
            String description,
            Integer sortOrder
    );

    /**
     * 删除题目附件。
     * @param problemId 题目 ID
     * @param attachmentId 附件 ID
     */
    void deleteAttachment(Long problemId, Long attachmentId);

    /**
     * 获取标签名称列表。
     */
    java.util.List<String> getTagNames(Long problemId);
}
