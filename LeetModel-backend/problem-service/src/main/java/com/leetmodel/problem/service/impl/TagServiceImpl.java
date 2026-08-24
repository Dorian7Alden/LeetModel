package com.leetmodel.problem.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.leetmodel.common.core.exception.BusinessException;
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

/**
 * 标签服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TagServiceImpl extends ServiceImpl<TagMapper, Tag> implements TagService {

    private final ProblemTagMapper problemTagMapper;

    @Override
    public Tag createTag(String name, TagType type) {
        checkNameDuplicate(name, null);
        Tag tag = new Tag();
        tag.setName(name);
        tag.setType(type.name());
        save(tag);
        log.info("创建标签: {} [ID: {}]", name, tag.getId());
        return tag;
    }

    @Override
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
        log.info("更新标签: {} [ID: {}]", name, id);
        return tag;
    }

    /**
     * 删除未被题目使用的标签。
     * @param id 标签 ID
     */
    @Override
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
        log.info("删除标签: {} [ID: {}]", tag.getName(), id);
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
