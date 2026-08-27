package com.leetmodel.admin.controller;

import com.leetmodel.common.api.feign.AiGatewayFeignClient;
import com.leetmodel.common.api.feign.AssistantFeignClient;
import com.leetmodel.common.api.feign.EvaluationFeignClient;
import com.leetmodel.common.api.feign.ProblemFeignClient;
import com.leetmodel.common.api.feign.RankingFeignClient;
import com.leetmodel.common.api.feign.ReviewFeignClient;
import com.leetmodel.common.api.feign.SubmissionFeignClient;
import com.leetmodel.common.api.feign.SuggestionFeignClient;
import com.leetmodel.common.api.feign.TeamFeignClient;
import com.leetmodel.common.api.feign.UserFeignClient;
import com.leetmodel.common.security.config.SaTokenAnnotationConfig;
import com.leetmodel.common.security.config.SecurityConfig;
import com.leetmodel.common.security.handler.AuthExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = {
        DashboardAuthorizationTest.TestApplication.class,
        DashboardController.class,
        SaTokenAnnotationConfig.class,
        SecurityConfig.class,
        AuthExceptionHandler.class
}, properties = {
        "spring.config.import=",
        "spring.cloud.nacos.config.enabled=false",
        "spring.cloud.nacos.discovery.enabled=false"
})
@AutoConfigureMockMvc
class DashboardAuthorizationTest {

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApplication {
    }

    @jakarta.annotation.Resource
    private MockMvc mockMvc;

    @MockBean UserFeignClient users;
    @MockBean TeamFeignClient teams;
    @MockBean ProblemFeignClient problems;
    @MockBean SubmissionFeignClient submissions;
    @MockBean ReviewFeignClient reviews;
    @MockBean SuggestionFeignClient suggestions;
    @MockBean RankingFeignClient rankings;
    @MockBean AssistantFeignClient assistant;
    @MockBean EvaluationFeignClient evaluations;
    @MockBean AiGatewayFeignClient aiGateway;

    @Test
    void unauthenticatedRequestMustNotReachDashboard() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard/stats"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101));

        verifyNoInteractions(users, teams, problems, submissions, reviews,
                suggestions, rankings, assistant, evaluations, aiGateway);
    }
}
