package com.leetmodel.problem.service;

import com.leetmodel.common.core.result.PageResult;
import com.leetmodel.problem.vo.ProblemFavoriteRecordVO;
import com.leetmodel.problem.vo.ProblemVO;

import java.util.List;

/**
 * 题目收藏业务接口。
 */
public interface ProblemFavoriteService {

    /**
     * 收藏指定题目。
     *
     * @param userId 用户 ID
     * @param problemId 题目 ID
     * @return 是否收藏成功
     */
    boolean addFavorite(Long userId, Long problemId);

    /**
     * 取消收藏指定题目。
     *
     * @param userId 用户 ID
     * @param problemId 题目 ID
     * @return 是否取消成功
     */
    boolean removeFavorite(Long userId, Long problemId);

    /**
     * 判断用户是否已收藏指定题目。
     *
     * @param userId 用户 ID
     * @param problemId 题目 ID
     * @return 是否已收藏
     */
    boolean isFavorited(Long userId, Long problemId);

    /**
     * 查询用户的所有收藏记录列表（按收藏时间升序排列，最先收藏的排在最前面）。
     *
     * @param userId 用户 ID
     * @return 收藏记录列表
     */
    List<ProblemFavoriteRecordVO> listFavoriteRecords(Long userId);

    /**
     * 分页查询用户收藏的题目列表（按收藏时间升序排列，最先收藏的排在最前面）。
     *
     * @param userId 用户 ID
     * @param page 页码
     * @param pageSize 每页条数
     * @return 题目分页结果
     */
    PageResult<ProblemVO> pageFavoriteProblems(Long userId, int page, int pageSize);
}
