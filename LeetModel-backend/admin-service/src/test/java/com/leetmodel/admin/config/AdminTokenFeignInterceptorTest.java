package com.leetmodel.admin.config;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;

class AdminTokenFeignInterceptorTest {

    @AfterEach
    void clearContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void shouldForwardCurrentAdminTokenWithoutInventingOne() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("satoken", "admin-token");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        RequestInterceptor interceptor = new AdminTokenFeignInterceptor().adminTokenRequestInterceptor();
        RequestTemplate template = new RequestTemplate();

        interceptor.apply(template);

        assertThat(template.headers().get("satoken")).containsExactly("admin-token");
        assertThat(template.headers()).doesNotContainKey("Authorization");
    }

    @Test
    void schedulerThreadWithoutRequestMustRemainTokenless() {
        RequestTemplate template = new RequestTemplate();

        new AdminTokenFeignInterceptor().adminTokenRequestInterceptor().apply(template);

        assertThat(template.headers()).doesNotContainKeys("satoken", "Authorization");
    }
}
