package com.leetmodel.problem.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.leetmodel.problem.entity.Tag;
import com.leetmodel.problem.enums.TagType;

/**
 * 标签服务接口。
 */
public interface TagService extends IService<Tag> {

    /**
     * 创建新标签（含名称唯一性校验）。
     *
     * @param name 标签名称，不能为 null
     * @param type 标签所属业务分类，不能为 null
     * @return 创建成功后的标签实体
     */
    Tag createTag(String name, TagType type);

    /**
     * 更新已有标签（含重名校验）。
     *
     * @param id   目标标签 ID，不能为 null
     * @param name 新标签名称，不能为 null
     * @param type 标签分类，不能为 null
     * @return 更新后的标签实体
     */
    Tag updateTag(Long id, String name, TagType type);

    /**
     * 删除未被任何题目引用的标签。
     *
     * @param id 目标标签 ID，不能为 null
     */
    void deleteTag(Long id);
}
