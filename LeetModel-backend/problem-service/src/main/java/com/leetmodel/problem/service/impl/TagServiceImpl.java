package com.leetmodel.problem.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.leetmodel.common.core.exception.BusinessException;
import com.leetmodel.problem.entity.Tag;
import com.leetmodel.problem.enums.ProblemErrorCode;
import com.leetmodel.problem.mapper.TagMapper;
import com.leetmodel.problem.service.TagService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 标签服务实现。
 *
 * @author LeetModel
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TagServiceImpl extends ServiceImpl<TagMapper, Tag> implements TagService {

    @Override
    public Tag createTag(String name) {
        checkNameDuplicate(name, null);
        Tag tag = new Tag();
        tag.setName(name);
        save(tag);
        log.info("创建标签: {} [ID: {}]", name, tag.getId());
        return tag;
    }

    @Override
    public Tag updateTag(Long id, String name) {
        Tag tag = getById(id);
        if (tag == null) {
            throw new BusinessException(ProblemErrorCode.TAG_NOT_FOUND);
        }
        checkNameDuplicate(name, id);
        tag.setName(name);
        updateById(tag);
        log.info("更新标签: {} [ID: {}]", name, id);
        return tag;
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
        if (exists(wrapper)) {
            throw new BusinessException(ProblemErrorCode.TAG_NAME_DUPLICATE);
        }
    }
}
