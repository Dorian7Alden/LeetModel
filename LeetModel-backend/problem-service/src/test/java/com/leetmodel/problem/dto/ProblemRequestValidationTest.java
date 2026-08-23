package com.leetmodel.problem.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 题目请求参数校验测试。
 */
class ProblemRequestValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    @DisplayName("创建题目拒绝非法状态")
    void createRequestRejectsInvalidStatus() {
        ProblemCreateRequest request = validCreateRequest();
        request.setStatus(4);

        Set<ConstraintViolation<ProblemCreateRequest>> violations = validator.validate(request);

        assertTrue(hasViolation(violations, "status"));
    }

    @Test
    @DisplayName("更新题目拒绝空白标题")
    void updateRequestRejectsBlankTitle() {
        ProblemUpdateRequest request = new ProblemUpdateRequest();
        request.setTitle("   ");

        Set<ConstraintViolation<ProblemUpdateRequest>> violations = validator.validate(request);

        assertTrue(hasViolation(violations, "title"));
    }

    @Test
    @DisplayName("创建题目递归校验外部链接")
    void createRequestValidatesNestedLink() {
        ProblemCreateRequest request = validCreateRequest();
        request.setLinks(List.of(new ProblemCreateRequest.LinkItem(
                "", "ftp://example.com", null, -1
        )));

        Set<ConstraintViolation<ProblemCreateRequest>> violations = validator.validate(request);

        assertTrue(hasViolation(violations, "links[0].title"));
        assertTrue(hasViolation(violations, "links[0].url"));
        assertTrue(hasViolation(violations, "links[0].sortOrder"));
    }

    @Test
    @DisplayName("分页查询拒绝非法赛事类型和标签 ID")
    void pageQueryRejectsInvalidFilters() {
        ProblemPageQuery query = new ProblemPageQuery();
        query.setContestType("OTHER");
        query.setTagId(0L);

        Set<ConstraintViolation<ProblemPageQuery>> violations = validator.validate(query);

        assertTrue(hasViolation(violations, "contestType"));
        assertTrue(hasViolation(violations, "tagId"));
    }

    private ProblemCreateRequest validCreateRequest() {
        ProblemCreateRequest request = new ProblemCreateRequest();
        request.setTitle("测试题目");
        request.setContestType("CUMCM");
        request.setDifficulty(2);
        return request;
    }

    private boolean hasViolation(
            Set<? extends ConstraintViolation<?>> violations,
            String propertyPath
    ) {
        return violations.stream()
                .anyMatch(violation -> propertyPath.equals(violation.getPropertyPath().toString()));
    }
}
