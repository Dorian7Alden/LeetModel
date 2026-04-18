package com.senior.leetmodelbackend.service.impl;

import com.senior.leetmodelbackend.mapper.TagMapper;
import com.senior.leetmodelbackend.pojo.entity.Tag;
import com.senior.leetmodelbackend.service.TagService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class TagServiceImpl implements TagService {

    private final TagMapper tagMapper;

    @Override
    public List<Tag> getTagsByCategoryId(Integer categoryId) {
        return tagMapper.getTagsByCategoryId(categoryId);
    }
}
