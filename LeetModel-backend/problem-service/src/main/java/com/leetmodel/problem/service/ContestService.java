package com.leetmodel.problem.service;

import com.leetmodel.problem.entity.Contest;

import java.util.List;

/**
 * 赛事基础数据服务接口。
 */
public interface ContestService {

    /**
     * 查询所有赛事字典数据列表。
     *
     * @return 赛事字典实体列表
     */
    List<Contest> list();

    /**
     * 更新指定赛事的编码与名称。
     *
     * @param id   目标赛事 ID，不能为 null
     * @param code 赛事编码，不能为 null
     * @param name 赛事名称，不能为 null
     * @return 更新后的赛事实体
     */
    Contest update(Long id, String code, String name);
}
