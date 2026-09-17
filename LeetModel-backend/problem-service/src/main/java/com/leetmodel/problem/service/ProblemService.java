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
     * 管理员分页组合条件查询题目列表（含标签与赛事信息）。
     *
     * @param query 分页与过滤参数对象，不能为 null
     * @return 分页包装的题目视图列表
     */
    IPage<ProblemVO> pageProblems(ProblemPageQuery query);

    /**
     * 查询题目详情（含未发布题目与全部附件）。
     *
     * @param id 目标题目 ID，不能为 null
     * @return 题目详情视图对象
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

    /**
     * 根据条件在已发布的题目中随机获取一道题目。
     *
     * @param query 过滤条件，不能为 null
     * @return 随机匹配的已发布题目详情
     */
    ProblemVO getRandomPublishedProblem(ProblemPageQuery query);

    /**
     * 查询供 AI 客服使用的最小已发布题目事实。
     * @param query 受控查询条件
     * @return 题目工具结果
     */
    AssistantProblemResultDTO queryForAssistant(AssistantProblemQueryDTO query);

    /**
     * 创建新的建模题目并持久化初始标签关联。
     *
     * @param request   题目创建参数对象，不能为 null
     * @param creatorId 创建人用户 ID，不能为 null
     * @return 创建成功后的题目视图对象
     */
    ProblemVO createProblem(ProblemCreateRequest request, Long creatorId);

    /**
     * 修改已有题目的基本信息、题面内容或发布状态。
     *
     * @param id      目标题目 ID，不能为 null
     * @param request 包含待修改内容的请求对象，不能为 null
     * @return 更新后的题目视图对象
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
     * 查询指定题目关联的所有标签名称集合。
     *
     * @param problemId 目标题目 ID，不能为 null
     * @return 标签名称字符串列表
     */
    java.util.List<String> getTagNames(Long problemId);
}
