package com.senior.leetmodelbackend.controller.tag;

import com.senior.leetmodelbackend.pojo.entity.Tag;
import com.senior.leetmodelbackend.service.TagService;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@AllArgsConstructor
public class GetTagsByCategoryId extends TagController {

    private TagService tagService;

    @GetMapping("/category/{categoryId}")
    public List<Tag> getTagsByCategoryId(@PathVariable Integer categoryId) {
        return tagService.getTagsByCategoryId(categoryId);
    }

}
