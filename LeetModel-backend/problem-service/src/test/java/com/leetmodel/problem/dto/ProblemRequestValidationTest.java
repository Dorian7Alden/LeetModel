package com.leetmodel.problem.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
    @DisplayName("分页查询拒绝非法赛事 ID 和标签 ID")
    void pageQueryRejectsInvalidFilters() {
        ProblemPageQuery query = new ProblemPageQuery();
        query.setContestId(0L);
        query.setTagIds(java.util.List.of(0L));

        Set<ConstraintViolation<ProblemPageQuery>> violations = validator.validate(query);

        assertTrue(hasViolation(violations, "contestId"));
        assertTrue(violations.stream().anyMatch(violation ->
                violation.getPropertyPath().toString().startsWith("tagIds")));
    }

    @Test
    @DisplayName("分页查询拒绝非白名单排序字段和方向")
    void pageQueryRejectsInvalidSort() {
        ProblemPageQuery query = new ProblemPageQuery();
        query.setSortBy("title desc");
        query.setSortOrder("sideways");

        Set<ConstraintViolation<ProblemPageQuery>> violations = validator.validate(query);

        assertTrue(hasViolation(violations, "sortBy"));
        assertTrue(hasViolation(violations, "sortOrder"));
    }

    private ProblemCreateRequest validCreateRequest() {
        ProblemCreateRequest request = new ProblemCreateRequest();
        request.setTitle("测试题目");
        request.setContestId(2L);
        request.setYear(2026);
        request.setStatementLanguage("ZH");
        request.setDurationMinutes(4320);
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
