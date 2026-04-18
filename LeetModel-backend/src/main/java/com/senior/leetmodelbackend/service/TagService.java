package com.senior.leetmodelbackend.service;

import com.senior.leetmodelbackend.pojo.entity.Tag;

import java.util.List;

public interface TagService {

    public List<Tag> getTagsByCategoryId(Integer categoryId);

}
