package com.leetmodel.problem.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.leetmodel.common.core.exception.BusinessException;
import com.leetmodel.common.cache.CacheInvalidator;
import com.leetmodel.problem.cache.ProblemPublicCacheService;
import com.leetmodel.problem.entity.Tag;
import com.leetmodel.problem.entity.ProblemTag;
import com.leetmodel.problem.enums.ProblemErrorCode;
import com.leetmodel.problem.enums.TagType;
import com.leetmodel.problem.mapper.ProblemTagMapper;
import com.leetmodel.problem.mapper.TagMapper;
import com.leetmodel.problem.service.TagService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 标签服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TagServiceImpl extends ServiceImpl<TagMapper, Tag> implements TagService {

    private final ProblemTagMapper problemTagMapper;
    private final CacheInvalidator cacheInvalidator;

    /**
     * 创建新标签（含名称唯一性校验）并失效公开题库缓存。
     *
     * @param name 标签名称，不能为 null
     * @param type 标签所属业务分类，不能为 null
     * @return 创建成功后的标签实体
     * @throws BusinessException 若标签名称已被占用
     */
    @Override
    @Transactional
    public Tag createTag(String name, TagType type) {
        checkNameDuplicate(name, null);
        Tag tag = new Tag();
        tag.setName(name);
        tag.setType(type.name());
        save(tag);
        recordPublicInvalidation();
        log.info("创建标签完成: id={}", tag.getId());
        return tag;
    }

    /**
     * 更新已有标签的名称与分类（若变更分类须保证未被题目引用）。
     *
     * @param id   目标标签 ID，不能为 null
     * @param name 新标签名称，不能为 null
     * @param type 目标标签分类，不能为 null
     * @return 更新后的标签实体
     * @throws BusinessException 若标签不存在、名称已被占用或标签正在使用中
     */
    @Override
    @Transactional
    public Tag updateTag(Long id, String name, TagType type) {
        Tag tag = getById(id);
        BusinessException.throwIf(tag == null, ProblemErrorCode.TAG_NOT_FOUND);
        if (!type.name().equals(tag.getType())) {
            LambdaQueryWrapper<ProblemTag> relationWrapper = new LambdaQueryWrapper<>();
            relationWrapper.eq(ProblemTag::getTagId, id);
            BusinessException.throwIf(
                    problemTagMapper.exists(relationWrapper),
                    ProblemErrorCode.TAG_IN_USE
            );
        }
        checkNameDuplicate(name, id);
        tag.setName(name);
        tag.setType(type.name());
        updateById(tag);
        recordPublicInvalidation();
        log.info("更新标签完成: id={}", id);
        return tag;
    }

    /**
     * 删除未被题目使用的标签。
     * @param id 标签 ID
     */
    @Override
    @Transactional
    public void deleteTag(Long id) {
        // 校验标签存在
        Tag tag = getById(id);
        BusinessException.throwIf(tag == null, ProblemErrorCode.TAG_NOT_FOUND);

        // 校验标签没有题目引用
        LambdaQueryWrapper<ProblemTag> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProblemTag::getTagId, id);
        BusinessException.throwIf(problemTagMapper.exists(wrapper), ProblemErrorCode.TAG_IN_USE);

        // 删除标签
        removeById(id);
        recordPublicInvalidation();
        log.info("删除标签完成: id={}", id);
    }

    /**
     * 在当前事务中记录公开题库失效事件。
     */
    private void recordPublicInvalidation() {
        cacheInvalidator.record(
                ProblemPublicCacheService.REGION,
                ProblemPublicCacheService.SCOPE,
                ProblemPublicCacheService.SCHEMA_VERSION
        );
    }

    /**
     * 检查标签名称是否已存在（排除指定 ID）。
     */
    private void checkNameDuplicate(String name, Long excludeId) {
        LambdaQueryWrapper<Tag> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Tag::getName, name);
        if (excludeId != null) {
            wrapper.ne(Tag::getId, excludeId);
        }
        BusinessException.throwIf(exists(wrapper), ProblemErrorCode.TAG_NAME_DUPLICATE);
    }
}
