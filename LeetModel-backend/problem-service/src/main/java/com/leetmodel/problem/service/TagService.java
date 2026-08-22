package com.leetmodel.problem.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.leetmodel.problem.entity.Tag;

/**
 * 标签服务接口。
 */
public interface TagService extends IService<Tag> {

    /**
     * 创建标签（含重名校验）。
     */
    Tag createTag(String name);

    /**
     * 更新标签（含重名校验）。
     */
    Tag updateTag(Long id, String name);
}
