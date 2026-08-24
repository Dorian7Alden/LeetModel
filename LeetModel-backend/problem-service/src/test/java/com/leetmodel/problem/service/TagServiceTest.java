package com.leetmodel.problem.service;

import com.leetmodel.common.core.exception.BusinessException;
import com.leetmodel.problem.entity.Tag;
import com.leetmodel.problem.enums.ProblemErrorCode;
import com.leetmodel.problem.enums.TagType;
import com.leetmodel.problem.mapper.ProblemTagMapper;
import com.leetmodel.problem.mapper.TagMapper;
import com.leetmodel.problem.service.impl.TagServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 标签服务单元测试。
 */
@ExtendWith(MockitoExtension.class)
class TagServiceTest {

    @Mock
    private TagMapper tagMapper;

    @Mock
    private ProblemTagMapper problemTagMapper;

    @InjectMocks
    private TagServiceImpl tagService;

    private Tag tag;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(tagService, "baseMapper", tagMapper);
        tag = new Tag();
        tag.setId(1L);
        tag.setName("预测");
        tag.setType(TagType.PROBLEM_TYPE.name());
    }

    @Test
    @DisplayName("创建标签成功")
    void createTagSuccess() {
        when(tagMapper.exists(any())).thenReturn(false);
        when(tagMapper.insert(any(Tag.class))).thenAnswer(invocation -> {
            Tag entity = invocation.getArgument(0);
            entity.setId(2L);
            return 1;
        });

        Tag result = tagService.createTag("优化", TagType.PROBLEM_TYPE);

        assertEquals(2L, result.getId());
        assertEquals("优化", result.getName());
        assertEquals(TagType.PROBLEM_TYPE.name(), result.getType());
        verify(tagMapper).insert(any(Tag.class));
    }

    @Test
    @DisplayName("创建标签失败 —— 名称重复")
    void createTagDuplicateName() {
        when(tagMapper.exists(any())).thenReturn(true);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> tagService.createTag("预测", TagType.PROBLEM_TYPE)
        );

        assertEquals(ProblemErrorCode.TAG_NAME_DUPLICATE.getCode(), exception.getCode());
        verify(tagMapper, never()).insert(any(Tag.class));
    }

    @Test
    @DisplayName("更新标签成功")
    void updateTagSuccess() {
        when(tagMapper.selectById(1L)).thenReturn(tag);
        when(tagMapper.exists(any())).thenReturn(false);
        when(tagMapper.updateById(any(Tag.class))).thenReturn(1);

        Tag result = tagService.updateTag(1L, "优化", TagType.PROBLEM_TYPE);

        assertEquals("优化", result.getName());
        verify(tagMapper).updateById(tag);
    }

    @Test
    @DisplayName("更新标签失败 —— 标签不存在")
    void updateTagNotFound() {
        when(tagMapper.selectById(999L)).thenReturn(null);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> tagService.updateTag(999L, "优化", TagType.PROBLEM_TYPE)
        );

        assertEquals(ProblemErrorCode.TAG_NOT_FOUND.getCode(), exception.getCode());
        verify(tagMapper, never()).updateById(any(Tag.class));
    }

    @Test
    @DisplayName("更新标签失败 —— 名称被其他标签占用")
    void updateTagDuplicateName() {
        when(tagMapper.selectById(1L)).thenReturn(tag);
        when(tagMapper.exists(any())).thenReturn(true);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> tagService.updateTag(1L, "优化", TagType.PROBLEM_TYPE)
        );

        assertEquals(ProblemErrorCode.TAG_NAME_DUPLICATE.getCode(), exception.getCode());
        verify(tagMapper, never()).updateById(any(Tag.class));
    }

    @Test
    @DisplayName("已使用标签不能更改类型")
    void updateUsedTagRejectsTypeChange() {
        when(tagMapper.selectById(1L)).thenReturn(tag);
        when(problemTagMapper.exists(any())).thenReturn(true);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> tagService.updateTag(1L, "预测", TagType.MODEL_ALGORITHM)
        );

        assertEquals(ProblemErrorCode.TAG_IN_USE.getCode(), exception.getCode());
        verify(tagMapper, never()).updateById(any(Tag.class));
    }

    @Test
    @DisplayName("删除标签成功")
    void deleteTagSuccess() {
        when(tagMapper.selectById(1L)).thenReturn(tag);
        when(problemTagMapper.exists(any())).thenReturn(false);
        when(tagMapper.deleteById(1L)).thenReturn(1);

        tagService.deleteTag(1L);

        verify(tagMapper).deleteById(1L);
    }

    @Test
    @DisplayName("删除标签失败 —— 标签不存在")
    void deleteTagNotFound() {
        when(tagMapper.selectById(999L)).thenReturn(null);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> tagService.deleteTag(999L)
        );

        assertEquals(ProblemErrorCode.TAG_NOT_FOUND.getCode(), exception.getCode());
        verify(problemTagMapper, never()).exists(any());
        verify(tagMapper, never()).deleteById(999L);
    }

    @Test
    @DisplayName("删除标签失败 —— 标签仍被题目使用")
    void deleteTagInUse() {
        when(tagMapper.selectById(1L)).thenReturn(tag);
        when(problemTagMapper.exists(any())).thenReturn(true);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> tagService.deleteTag(1L)
        );

        assertEquals(ProblemErrorCode.TAG_IN_USE.getCode(), exception.getCode());
        verify(tagMapper, never()).deleteById(1L);
    }
}
