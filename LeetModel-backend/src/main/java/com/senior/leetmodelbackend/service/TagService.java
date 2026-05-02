package com.senior.leetmodelbackend.service;

import com.senior.leetmodelbackend.mapper.TagMapper;
import com.senior.leetmodelbackend.pojo.entity.Tag;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class TagService {

    private final TagMapper tagMapper;

    public List<Tag> getTagsByCategoryId(Integer categoryId) {
        return tagMapper.getTagsByCategoryId(categoryId);
    }
}
