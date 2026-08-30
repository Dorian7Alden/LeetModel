package com.leetmodel.problem.service;

import com.leetmodel.problem.entity.Contest;

import java.util.List;

/** 赛事基础数据服务。 */
public interface ContestService {
    List<Contest> list();

    Contest update(Long id, String code, String name);
}
