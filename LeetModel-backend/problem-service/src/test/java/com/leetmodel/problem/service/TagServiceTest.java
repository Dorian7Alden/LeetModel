package com.leetmodel.problem.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.leetmodel.common.core.exception.BusinessException;
import com.leetmodel.common.cache.CacheInvalidator;
import com.leetmodel.problem.entity.Tag;
import com.leetmodel.problem.entity.ProblemTag;
import com.leetmodel.problem.vo.TagAdminVO;
import java.util.List;
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

    @Mock
    private CacheInvalidator cacheInvalidator;

    @InjectMocks
    private TagServiceImpl tagService;

    private Tag tag;

    @Test
    @DisplayName("查询标签列表附带使用量统计成功")
    void listTagsWithUsageSuccess() {
        Tag tag2 = new Tag();
        tag2.setId(2L);
        tag2.setName("机器学习");
        tag2.setType(TagType.MODEL_ALGORITHM.name());

        when(tagMapper.selectList(any())).thenReturn(List.of(tag, tag2));

        ProblemTag pt1 = new ProblemTag();
        pt1.setTagId(1L);
        ProblemTag pt2 = new ProblemTag();
        pt2.setTagId(1L);
        ProblemTag pt3 = new ProblemTag();
        pt3.setTagId(2L);
        when(problemTagMapper.selectList(any())).thenReturn(List.of(pt1, pt2, pt3));

        List<TagAdminVO> results = tagService.listTagsWithUsage();

        assertEquals(2, results.size());
        TagAdminVO vo1 = results.stream().filter(v -> v.getId().equals(1L)).findFirst().orElseThrow();
        assertEquals(2L, vo1.getProblemCount());
        TagAdminVO vo2 = results.stream().filter(v -> v.getId().equals(2L)).findFirst().orElseThrow();
        assertEquals(1L, vo2.getProblemCount());
    }

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(tagService, "baseMapper", tagMapper);
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(configuration, ""),
                ProblemTag.class
        );
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
